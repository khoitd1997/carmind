#include "dof.h"

#include <assert.h>

#include "app_error.h"
#include "nrf_delay.h"

static nrf_drv_twi_t const* twiModule;

static s8 bno055I2CBusWrite(u8 devAddr, u8 regAddr, u8* regData, u8 cnt) {
  assert(cnt + 1 <= BNO055_TWI_BUF_LEN);

  u8 array[BNO055_TWI_BUF_LEN] = {regAddr};
  for (int i = 0; i < cnt; i++) { array[i + 1] = *(regData + i); }

  return (twiTx(twiModule, devAddr, array, cnt + 1, false) == NRF_SUCCESS ? 0 : -1);
}
static s8 bno055I2CBusRead(u8 devAddr, u8 regAddr, u8* regData, u8 cnt) {
  if (twiTx(twiModule, devAddr, &regAddr, sizeof(regAddr), true) != NRF_SUCCESS) { return -1; }
  return (twiRx(twiModule, devAddr, regData, cnt) == NRF_SUCCESS ? 0 : -1);
}
static void bno055DelayMs(u32 ms) { nrf_delay_ms((unsigned int)ms); }

ret_code_t dofInit(DOFSensor*                 dof,
                   nrf_drv_twi_t const*       twi,
                   const nrf_drv_gpiote_pin_t intPin,
                   DofIntHandler              intHandler) {
  dof->devAddr = BNO055_I2C_ADDR1;
  dof->intPin  = intPin;
  twiModule    = twi;

  struct bno055_t bno055 = {0};
  bno055.bus_write       = bno055I2CBusWrite;
  bno055.bus_read        = bno055I2CBusRead;
  bno055.delay_msec      = bno055DelayMs;
  bno055.dev_addr        = dof->devAddr;

  s32 ret = bno055_init(&bno055);
  ret += bno055_set_power_mode(BNO055_POWER_MODE_NORMAL);
  ret += bno055_set_sys_rst(BNO055_BIT_ENABLE);

  ret += bno055_init(&bno055);
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

  //   bno055_get_intr_stat_accel_no_motion
  //   default 4g
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

  ret_code_t errCode = nrf_drv_gpiote_init();
  APP_ERROR_CHECK(errCode);

  nrf_drv_gpiote_in_config_t dofPinCfg = GPIOTE_CONFIG_IN_SENSE_TOGGLE(true);
  dofPinCfg.pull                       = NRF_GPIO_PIN_PULLDOWN;
  dofPinCfg.sense                      = NRF_GPIOTE_POLARITY_LOTOHI;

  errCode = nrf_drv_gpiote_in_init(intPin, &dofPinCfg, intHandler);
  APP_ERROR_CHECK(errCode);

  nrf_drv_gpiote_in_event_enable(intPin, true);

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