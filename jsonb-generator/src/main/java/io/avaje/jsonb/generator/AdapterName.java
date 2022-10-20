package io.avaje.jsonb.generator;

import javax.lang.model.element.TypeElement;

class AdapterName {

  final String shortName;
  final String adapterPackage;
  final String fullName;

  AdapterName(TypeElement origin) {
    final String originName = origin.getQualifiedName().toString();
    final String name = origin.getSimpleName().toString();
    String originPackage = Util.packageOf(originName);
    if (origin.getNestingKind().isNested()) {
      final String parent = Util.shortName(originPackage);
      originPackage = Util.packageOf(originPackage);
      shortName = parent + "$" + name;
    } else {
      shortName = name;
    }
    this.adapterPackage = "".equals(originPackage) ? "jsonb" : originPackage + ".jsonb";
    this.fullName = adapterPackage + "." + shortName + "JsonAdapter";
  }

  String shortName() {
    return shortName;
  }

  String adapterPackage() {
    return adapterPackage;
  }

  String fullName() {
    return fullName;
  }
}
