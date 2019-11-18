/**
 * Copyright (c) 2015 - 2019, Nordic Semiconductor ASA
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form, except as embedded into a Nordic
 *    Semiconductor ASA integrated circuit in a product or a software update for
 *    such product, must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. Neither the name of Nordic Semiconductor ASA nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * 4. This software, with or without modification, must only be used with a
 *    Nordic Semiconductor ASA integrated circuit.
 *
 * 5. Any software provided in binary form under this license must not be reverse
 *    engineered, decompiled, modified and/or disassembled.
 *
 * THIS SOFTWARE IS PROVIDED BY NORDIC SEMICONDUCTOR ASA "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY, NONINFRINGEMENT, AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NORDIC SEMICONDUCTOR ASA OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
/** @file
 * @defgroup tw_sensor_example main.c
 * @{
 * @ingroup nrf_twi_example
 * @brief TWI Sensor Example main file.
 *
 * This file contains the source code for a sample application using TWI.
 *
 */

#include <assert.h>
#include <stdio.h>

#include "app_error.h"
#include "app_util_platform.h"
#include "boards.h"
#include "nrf_delay.h"
#include "nrf_drv_twi.h"

#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"

#include "bno055.h"

#define BNO055_TWI_BUF_LEN 100
typedef struct {
  // orientation in degree
  double eulerRoll;
  double eulerPitch;
  double eulerYaw;

  // linear acceleration m/s^2
  double linearX;
  double linearY;
  double linearZ;

  // gyro dps
  double angularX;
  double angularY;
  double angularZ;
} DOFData;
typedef struct {
  struct bno055_t bno055;
  uint8_t         devAddr;
} DOFSensor;
static DOFSensor dofSensor;

static const nrf_drv_twi_t twiModule   = NRF_DRV_TWI_INSTANCE(0);
static volatile bool       twiXferDone = false;

ret_code_t twiTx(
    nrf_drv_twi_t const* twi, uint8_t devAddr, uint8_t const* data, uint8_t length, bool no_stop) {
  twiXferDone        = false;
  ret_code_t errCode = nrf_drv_twi_tx(twi, devAddr, data, length, no_stop);
  APP_ERROR_CHECK(errCode);
  while (twiXferDone == false) {}

  return errCode;
}
ret_code_t twiRx(nrf_drv_twi_t const* twi, uint8_t devAddr, uint8_t* data, uint8_t length) {
  twiXferDone        = false;
  ret_code_t errCode = nrf_drv_twi_rx(twi, devAddr, data, length);
  APP_ERROR_CHECK(errCode);
  while (twiXferDone == false) {}

  return errCode;
}

s8 bno055I2CBusWrite(u8 devAddr, u8 regAddr, u8* regData, u8 cnt) {
  assert(cnt + 1 <= BNO055_TWI_BUF_LEN);

  u8 array[BNO055_TWI_BUF_LEN] = {regAddr};
  for (int i = 0; i < cnt; i++) { array[i + 1] = *(regData + i); }

  return (twiTx(&twiModule, devAddr, array, cnt + 1, false) == NRF_SUCCESS ? 0 : -1);
}
s8 bno055I2CBusRead(u8 devAddr, u8 regAddr, u8* regData, u8 cnt) {
  if (twiTx(&twiModule, devAddr, &regAddr, sizeof(regAddr), true) != NRF_SUCCESS) { return -1; }
  return (twiRx(&twiModule, devAddr, regData, cnt) == NRF_SUCCESS ? 0 : -1);
}
void bno055DelayMs(u32 ms) { nrf_delay_ms((unsigned int)ms); }

ret_code_t dofInit(DOFSensor* dof) {
  dof->devAddr = BNO055_I2C_ADDR1;

  dof->bno055.bus_write  = bno055I2CBusWrite;
  dof->bno055.bus_read   = bno055I2CBusRead;
  dof->bno055.delay_msec = bno055DelayMs;
  dof->bno055.dev_addr   = dof->devAddr;

  s32 ret = bno055_init(&(dof->bno055));
  ret += bno055_set_power_mode(BNO055_POWER_MODE_NORMAL);
  ret += bno055_set_operation_mode(BNO055_OPERATION_MODE_NDOF);

  // bno055_get_intr_mask_gyro_highrate
  // default 2000dps so 62.5 dps per bit
  ret += bno055_set_gyro_highrate_axis_enable(BNO055_GYRO_HIGHRATE_Z_AXIS, BNO055_BIT_ENABLE);
  ret += bno055_set_gyro_highrate_filter(BNO055_GYRO_FILTERED_CONFIG);
  ret += bno055_set_gyro_highrate_z_thres(2);
  ret += bno055_set_gyro_highrate_z_hyst(0);
  ret += bno055_set_gyro_highrate_z_durn(30);  // 2.5 ms per LSB
  ret += bno055_set_intr_mask_gyro_highrate(BNO055_BIT_ENABLE);
  ret += bno055_set_intr_gyro_highrate(BNO055_BIT_ENABLE);

  // bno055_get_intr_stat_accel_no_motion
  // default 4g
  ret += bno055_set_accel_any_motion_no_motion_axis_enable(BNO055_ACCEL_ANY_MOTION_NO_MOTION_X_AXIS,
                                                           BNO055_BIT_ENABLE);
  ret += bno055_set_accel_any_motion_no_motion_axis_enable(BNO055_ACCEL_ANY_MOTION_NO_MOTION_Y_AXIS,
                                                           BNO055_BIT_ENABLE);
  ret += bno055_set_accel_any_motion_no_motion_axis_enable(BNO055_ACCEL_ANY_MOTION_NO_MOTION_Z_AXIS,
                                                           BNO055_BIT_ENABLE);
  ret += bno055_set_accel_slow_no_motion_thres(2);
  ret += bno055_set_accel_slow_no_motion_durn(3);
  ret += bno055_set_intr_mask_accel_no_motion(BNO055_BIT_ENABLE);
  ret += bno055_set_intr_accel_no_motion(BNO055_BIT_ENABLE);
  ret += bno055_set_accel_slow_no_motion_enable(0);

  //   bno055_write_mag_offset

  return (ret ? NRF_ERROR_INTERNAL : NRF_SUCCESS);
}

ret_code_t dofDeinit(DOFSensor* dof) {
  return bno055_set_power_mode(BNO055_POWER_MODE_SUSPEND) ? NRF_ERROR_INTERNAL : NRF_SUCCESS;
}

ret_code_t dofRead(DOFSensor* dof, DOFData* data) {
  s32 ret = bno055_set_operation_mode(BNO055_OPERATION_MODE_NDOF);

  struct bno055_gyro_double_t gyroData;
  ret += bno055_convert_double_gyro_xyz_dps(&gyroData);
  data->angularX = gyroData.x;
  data->angularY = gyroData.y;
  data->angularZ = gyroData.z;

  struct bno055_euler_double_t eulerData;
  ret += bno055_convert_double_euler_hpr_deg(&eulerData);
  data->eulerYaw   = eulerData.h;
  data->eulerRoll  = eulerData.r;
  data->eulerPitch = eulerData.p;

  struct bno055_linear_accel_double_t linearData;
  ret += bno055_convert_double_linear_accel_xyz_msq(&linearData);
  data->linearX = linearData.x;
  data->linearY = linearData.y;
  data->linearZ = linearData.z;

  return (ret ? NRF_ERROR_INTERNAL : NRF_SUCCESS);
}

__STATIC_INLINE void data_handler(uint8_t temp) {
  NRF_LOG_INFO("Temperature: %d Celsius degrees.", temp);
}

void twi_handler(nrf_drv_twi_evt_t const* p_event, void* p_context) {
  switch (p_event->type) {
    case NRF_DRV_TWI_EVT_DONE:
      //   if (p_event->xfer_desc.type == NRF_DRV_TWI_XFER_RX) { data_handler(m_sample); }
      twiXferDone = true;
      break;
    default:
      break;
  }
}

void twiInit(void) {
  const nrf_drv_twi_config_t twiConfig = {.scl                = ARDUINO_SCL_PIN,
                                          .sda                = ARDUINO_SDA_PIN,
                                          .frequency          = NRF_DRV_TWI_FREQ_100K,
                                          .interrupt_priority = APP_IRQ_PRIORITY_HIGH,
                                          .clear_bus_init     = false};

  ret_code_t errCode = nrf_drv_twi_init(&twiModule, &twiConfig, twi_handler, NULL);
  APP_ERROR_CHECK(errCode);

  nrf_drv_twi_enable(&twiModule);
}

int main(void) {
  APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  NRF_LOG_INFO("\r\nTWI sensor example started.");
  NRF_LOG_FLUSH();
  twiInit();
  dofInit(&dofSensor);

  DOFData dofData = {0};
  while (true) {
    nrf_delay_ms(100);
    dofRead(&dofSensor, &dofData);
    NRF_LOG_INFO("\r\n");
    NRF_LOG_INFO(NRF_LOG_FLOAT_MARKER "\n", NRF_LOG_FLOAT(dofData.eulerRoll));
    NRF_LOG_INFO(NRF_LOG_FLOAT_MARKER "\n", NRF_LOG_FLOAT(dofData.eulerPitch));
    NRF_LOG_INFO(NRF_LOG_FLOAT_MARKER "\n", NRF_LOG_FLOAT(dofData.eulerYaw));

    // do { __WFE(); } while (twiXferDone == false);

    NRF_LOG_FLUSH();
  }
}

/** @} */
