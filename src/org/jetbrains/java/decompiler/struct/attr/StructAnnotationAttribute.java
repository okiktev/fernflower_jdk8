// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructAnnotationAttribute extends StructGeneralAttribute {
  private List<AnnotationExprent> annotations;

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    annotations = parseAnnotations(pool, data);
  }

  public static List<AnnotationExprent> parseAnnotations(ConstantPool pool, DataInputStream data) throws IOException {
    int len = data.readUnsignedShort();
    if (len > 0) {
      List<AnnotationExprent> annotations = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        annotations.add(parseAnnotation(data, pool));
      }
      return annotations;
    }
    else {
      return Collections.emptyList();
    }
  }

  public static @NotNull AnnotationExprent parseAnnotation(DataInputStream data, ConstantPool pool) throws IOException {
    String className = pool.getPrimitiveConstant(data.readUnsignedShort()).getString();

    List<String> names;
    List<Exprent> values;
    int len = data.readUnsignedShort();
    if (len > 0) {
      names = new ArrayList<>(len);
      values = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        names.add(pool.getPrimitiveConstant(data.readUnsignedShort()).getString());
        values.add(parseAnnotationElement(data, pool));
      }
    }
    else {
      names = Collections.emptyList();
      values = Collections.emptyList();
    }

    return new AnnotationExprent(new VarType(className).getValue(), names, values);
  }

  public static Exprent parseAnnotationElement(DataInputStream data, ConstantPool pool) throws IOException {
    int tag = data.readUnsignedByte();

    switch (tag) {
      case 'e': // enum constant
        String className = pool.getPrimitiveConstant(data.readUnsignedShort()).getString();
        String constName = pool.getPrimitiveConstant(data.readUnsignedShort()).getString();
        FieldDescriptor descr = FieldDescriptor.parseDescriptor(className);
        return new FieldExprent(constName, descr.type.getValue(), true, null, descr, null);
      case 'c': // class
        String descriptor = pool.getPrimitiveConstant(data.readUnsignedShort()).getString();
        VarType type = FieldDescriptor.parseDescriptor(descriptor).type;

        String value = getClassName(type);
        return new ConstExprent(VarType.VARTYPE_CLASS, value, null);
      case '[': // array
        List<Exprent> elements = Collections.emptyList();
        int len = data.readUnsignedShort();
        if (len > 0) {
          elements = new ArrayList<>(len);
          for (int i = 0; i < len; i++) {
            elements.add(parseAnnotationElement(data, pool));
          }
        }

        VarType newType;
        if (elements.isEmpty()) {
          newType = new VarType(CodeConstants.TYPE_OBJECT, 1, "java/lang/Object");
        }
        else {
          VarType elementType = elements.get(0).getExprType();
          newType = new VarType(elementType.getType(), 1, elementType.getValue());
        }

        NewExprent newExpr = new NewExprent(newType, Collections.emptyList(), null);
        newExpr.setDirectArrayInit(true);
        newExpr.setLstArrayElements(elements);
        return newExpr;
      case '@': // annotation
        return parseAnnotation(data, pool);
      default:
        PrimitiveConstant cn = pool.getPrimitiveConstant(data.readUnsignedShort());
        switch (tag) {
          case 'B': return new ConstExprent(VarType.VARTYPE_BYTE, cn.value, null);
          case 'C': return new ConstExprent(VarType.VARTYPE_CHAR, cn.value, null);
          case 'D': return new ConstExprent(VarType.VARTYPE_DOUBLE, cn.value, null);
          case 'F': return new ConstExprent(VarType.VARTYPE_FLOAT, cn.value, null);
          case 'I': return new ConstExprent(VarType.VARTYPE_INT, cn.value, null);
          case 'J': return new ConstExprent(VarType.VARTYPE_LONG, cn.value, null);
          case 'S': return new ConstExprent(VarType.VARTYPE_SHORT, cn.value, null);
          case 'Z': return new ConstExprent(VarType.VARTYPE_BOOLEAN, cn.value, null);
          case 's': return new ConstExprent(VarType.VARTYPE_STRING, cn.value, null);
          default: throw new RuntimeException("invalid element type!");
        }
    }
  }

  private static String getClassName(VarType type) {
      switch (type.getType()) {
      case CodeConstants.TYPE_OBJECT:return type.getValue();
      case CodeConstants.TYPE_BYTE:return byte.class.getName();
      case CodeConstants.TYPE_CHAR:return char.class.getName();
      case CodeConstants.TYPE_DOUBLE:return double.class.getName();
      case CodeConstants.TYPE_FLOAT:return float.class.getName();
      case CodeConstants.TYPE_INT:return int.class.getName();
      case CodeConstants.TYPE_LONG:return long.class.getName();
      case CodeConstants.TYPE_SHORT:return short.class.getName();
      case CodeConstants.TYPE_BOOLEAN:return boolean.class.getName();
      case CodeConstants.TYPE_VOID:return void.class.getName();
      default: throw new RuntimeException("invalid class type: " + type.getType());
      }
  }

  public List<AnnotationExprent> getAnnotations() {
    return annotations;
  }
}