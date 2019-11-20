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

#include "app_error.h"
#include "app_util_platform.h"
#include "boards.h"

#include "nrf_delay.h"
#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"

#include "twi_utils.h"

#include "dof.h"

#define DOF_INT_PIN 20
static DOFSensor dofSensor;

static const nrf_drv_twi_t twiModule = NRF_DRV_TWI_INSTANCE(0);

void dofIntHandler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
  if (DOF_INT_PIN == pin && NRF_GPIOTE_POLARITY_LOTOHI == action) {
    u8 intStat;
    bno055_get_intr_stat(&intStat);
    bno055_set_intr_rst(BNO055_BIT_ENABLE);
    //   BNO055_GET_BITSLICE(data_u8r, BNO055_INTR_STAT_GYRO_ANY_MOTION);
    NRF_LOG_INFO("\r\nIn interrupt %d", intStat);
    NRF_LOG_FLUSH();
  }
}

__STATIC_INLINE void data_handler(uint8_t temp) {
  NRF_LOG_INFO("Temperature: %d Celsius degrees.", temp);
}
void twiHandler(nrf_drv_twi_evt_t const* p_event, void* p_context) {
  switch (p_event->type) {
    case NRF_DRV_TWI_EVT_DONE:
      //   if (p_event->xfer_desc.type == NRF_DRV_TWI_XFER_RX) { data_handler(m_sample); }
      //   twiXferDone = true;
      break;
    default:
      break;
  }
}

int main(void) {
  APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
  NRF_LOG_DEFAULT_BACKENDS_INIT();

  NRF_LOG_INFO("\r\nTWI sensor example started.");
  NRF_LOG_FLUSH();

  {
    const nrf_drv_twi_config_t twiConfig = {.scl                = ARDUINO_SCL_PIN,
                                            .sda                = ARDUINO_SDA_PIN,
                                            .frequency          = NRF_DRV_TWI_FREQ_100K,
                                            .interrupt_priority = APP_IRQ_PRIORITY_HIGH,
                                            .clear_bus_init     = false};
    twiInit(&twiModule, &twiConfig, twiHandler);
  }
  dofInit(&dofSensor, &twiModule, DOF_INT_PIN, dofIntHandler);

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
