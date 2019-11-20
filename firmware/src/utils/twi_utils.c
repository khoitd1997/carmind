#include "twi_utils.h"

ret_code_t twiTx(
    nrf_drv_twi_t const* twi, uint8_t devAddr, uint8_t const* data, uint8_t length, bool no_stop) {
  ret_code_t errCode = nrf_drv_twi_tx(twi, devAddr, data, length, no_stop);
  APP_ERROR_CHECK(errCode);
  while (nrf_drv_twi_is_busy(twi)) {}

  return errCode;
}
ret_code_t twiRx(nrf_drv_twi_t const* twi, uint8_t devAddr, uint8_t* data, uint8_t length) {
  ret_code_t errCode = nrf_drv_twi_rx(twi, devAddr, data, length);
  APP_ERROR_CHECK(errCode);
  while (nrf_drv_twi_is_busy(twi)) {}

  return errCode;
}

void twiInit(nrf_drv_twi_t const*        twi,
             nrf_drv_twi_config_t const* twiConfig,
             nrf_drv_twi_evt_handler_t   twiHandler) {
  ret_code_t errCode = nrf_drv_twi_init(twi, twiConfig, twiHandler, NULL);
  APP_ERROR_CHECK(errCode);

  nrf_drv_twi_enable(twi);
}