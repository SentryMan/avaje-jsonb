package io.avaje.jsonb.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For internal use, holds metadata on generated adapters for use by code generation (Java
 * annotation processing).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface MetaData {

  /** The generated JsonAdapters. */
  Class<?>[] value();
}
