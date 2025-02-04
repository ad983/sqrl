/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.util.data;

import java.util.Set;


public class Sensors extends UseCaseExample {

  public static final Sensors INSTANCE = new Sensors("");

  public static final Sensors INSTANCE_EPOCH = new Sensors("epoch");


  protected Sensors(String variant) {
    super(variant, Set.of("sensors","sensorreading","machinegroup"),
        script("sensors-teaser","machine","minreadings"));
  }
}
