package com.datasqrl.config;

import com.datasqrl.error.ErrorCode;
import com.datasqrl.error.ErrorHandler;
import com.datasqrl.error.ErrorLocation;
import com.datasqrl.error.ErrorMessage;
import com.google.auto.service.AutoService;
import org.apache.commons.configuration2.ex.ConfigurationException;

@AutoService(ErrorHandler.class)
public class ConfigurationExceptionHandler implements ErrorHandler<ConfigurationException> {

  @Override
  public ErrorMessage handle(ConfigurationException e, ErrorLocation baseLocation) {
    return new ErrorMessage.Implementation(ErrorCode.CONFIG_EXCEPTION, e.getMessage(), baseLocation, ErrorMessage.Severity.FATAL);
  }

  @Override
  public Class getHandleClass() {
    return ConfigurationException.class;
  }
}