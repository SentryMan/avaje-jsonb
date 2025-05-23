package io.avaje.jsonb.generator;

import static io.avaje.jsonb.generator.APContext.createSourceFile;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.tools.JavaFileObject;

final class SimpleComponentWriter {

  private final ComponentMetaData metaData;
  private final Set<String> importTypes = new TreeSet<>();
  private Append writer;
  private JavaFileObject fileObject;
  private String fullName;
  private String packageName;

  SimpleComponentWriter(ComponentMetaData metaData) {
    this.metaData = metaData;
  }

  void initialise(boolean pkgPrivate) throws IOException {
    fullName = metaData.fullName(pkgPrivate);
    packageName = Util.packageOf(fullName);
    if (fileObject == null) {
      fileObject = createSourceFile(fullName);
    }
    if (!metaData.isEmpty()) {
      ProcessingContext.addJsonSpi(fullName);
    }
  }

  private Writer createFileWriter() throws IOException {
    return fileObject.openWriter();
  }

  void write() throws IOException {
    writer = new Append(createFileWriter());
    writePackage();
    writeImports();
    writeClassStart();
    writeRegister();
    writeClassEnd();
    writer.close();
  }

  private void writeRegister() {
    writer.append("  @Override").eol();
    writer.append("  public void register(Jsonb.Builder builder) {").eol();

    for (final String adapterFullName : metaData.withTypes()) {
      final String adapterShortName = Util.shortName(adapterFullName);
      writer.append("    builder.add(%s.class, %s::new);", adapterShortName, adapterShortName).eol();
    }
    for (final String adapterFullName : metaData.allFactories()) {
      final String adapterShortName = Util.shortName(adapterFullName);
      writer.append("    builder.add(%s.FACTORY);", adapterShortName).eol();
    }
    for (final String adapterFullName : metaData.all()) {
      final String adapterShortName = Util.shortName(adapterFullName);
      final String typeName = Util.shortType(Util.baseTypeOfAdapter(adapterFullName).replace("$", "."));
      writer.append("    builder.add(%s.class, %s::new);", typeName, adapterShortName).eol();
    }
    writer.append("  }").eol().eol();
  }

  private void writeClassEnd() {
    writer.append("}").eol();
  }

  private void writeClassStart() {
    final String shortName = Util.shortName(fullName);
    writer.append("@Generated(\"io.avaje.jsonb.generator\")").eol();
    final List<String> factories = metaData.allFactories();
    if (!factories.isEmpty()) {
      writer.append("@MetaData.JsonFactory({");
      writeMetaDataEntry(factories);
      writer.append("})").eol();
    }
    writer.append("@MetaData({");
    final List<String> all = new ArrayList<>(metaData.all());
    all.addAll(metaData.withTypes());
    writeMetaDataEntry(all);
    writer.append("})").eol();

    writer.append("public %sclass %s implements GeneratedComponent {", Util.valhalla(), shortName).eol().eol();
  }

  private void writeMetaDataEntry(List<String> entries) {
    for (int i = 0, size = entries.size(); i < size; i++) {
      if (i > 0) {
        writer.append(", ");
      }
      writer.append("%s.class", Util.shortName(entries.get(i)));
    }
  }


  private void writeImports() {
    importTypes.add(Constants.JSONB);
    importTypes.addAll(metaData.allImports());
    importTypes.add("io.avaje.jsonb.spi.Generated");
    importTypes.add("io.avaje.jsonb.spi.GeneratedComponent");
    importTypes.add("io.avaje.jsonb.spi.MetaData");

    for (final String importType : importTypes) {
      if (Util.validImportType(importType, packageName)) {
        writer.append("import %s;", importType).eol();
      }
    }
    writer.eol();
  }

  private void writePackage() {
    if (packageName != null && !packageName.isEmpty()) {
      writer.append("package %s;", packageName).eol().eol();
    }
  }
}
