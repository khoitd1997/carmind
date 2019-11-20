#pragma once

#include "bno055.h"
#include "nrf_drv_gpiote.h"

#include "twi_utils.h"

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
  uint8_t              devAddr;
  nrf_drv_gpiote_pin_t intPin;
} DOFSensor;

typedef void (*DofIntHandler)(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action);

ret_code_t dofInit(DOFSensor*                 dof,
                   nrf_drv_twi_t const*       twi,
                   const nrf_drv_gpiote_pin_t intPin,
                   DofIntHandler              intHandler);
ret_code_t dofDeinit(DOFSensor* dof);
ret_code_t dofRead(DOFSensor* dof, DOFData* data);