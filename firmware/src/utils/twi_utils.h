#pragma once

#include "app_error.h"
#include "nrf_drv_twi.h"

ret_code_t twiTx(
    nrf_drv_twi_t const* twi, uint8_t devAddr, uint8_t const* data, uint8_t length, bool no_stop);
ret_code_t twiRx(nrf_drv_twi_t const* twi, uint8_t devAddr, uint8_t* data, uint8_t length);
void       twiInit(nrf_drv_twi_t const*        twi,
                   nrf_drv_twi_config_t const* twiConfig,
                   nrf_drv_twi_evt_handler_t   twiHandler);