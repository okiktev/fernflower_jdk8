// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TargetInfo;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TypeAnnotation;
import org.jetbrains.java.decompiler.struct.StructTypePathEntry;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructTypeAnnotationAttribute extends StructGeneralAttribute {
  private List<TypeAnnotation> annotations = Collections.emptyList();

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    int len = data.readUnsignedShort();
    if (len > 0) {
      annotations = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        annotations.add(parse(data, pool));
      }
    }
    else {
      annotations = Collections.emptyList();
    }
  }

  private static TypeAnnotation parse(DataInputStream data, ConstantPool pool) throws IOException {
    int targetType = data.readUnsignedByte();

    TargetInfo targetInfo = getTargetInfo(data, targetType);

    int pathLength = data.readUnsignedByte();
    List<StructTypePathEntry> paths = new ArrayList<>(pathLength);
    for (int i = 0; i < pathLength; i++) {
      paths.add(i, new StructTypePathEntry(data.readUnsignedByte(), data.readUnsignedByte()));
    }

    AnnotationExprent annotation = StructAnnotationAttribute.parseAnnotation(data, pool);

    return new TypeAnnotation(targetType, targetInfo, paths, annotation);
  }

    private static TargetInfo getTargetInfo(DataInputStream data,int targetType) throws IOException{
        switch (targetType) {
          case TypeAnnotation.CLASS_TYPE_PARAMETER:case TypeAnnotation.METHOD_TYPE_PARAMETER:
            return new TargetInfo.TypeParameterTarget(data.readUnsignedByte());
          case TypeAnnotation.SUPER_TYPE_REFERENCE:
            return new TargetInfo.SupertypeTarget(data.readUnsignedShort());
          case TypeAnnotation.CLASS_TYPE_PARAMETER_BOUND:case TypeAnnotation.METHOD_TYPE_PARAMETER_BOUND:
            return new TargetInfo.TypeParameterBoundTarget(data.readUnsignedByte(), data.readUnsignedByte());
          case TypeAnnotation.FIELD:case TypeAnnotation.METHOD_RETURN_TYPE:case TypeAnnotation.METHOD_RECEIVER:
            return new TargetInfo.EmptyTarget();
          case TypeAnnotation.METHOD_PARAMETER:
            return new TargetInfo.FormalParameterTarget(data.readUnsignedByte());
          case TypeAnnotation.THROWS_REFERENCE:
            return new TargetInfo.ThrowsTarget(data.readUnsignedShort());
          case TypeAnnotation.LOCAL_VARIABLE:case TypeAnnotation.RESOURCE_VARIABLE:
            int tableLength = data.readUnsignedShort();
            TargetInfo.LocalvarTarget.Offsets[] offsets = new TargetInfo.LocalvarTarget.Offsets[tableLength];
            for (int i = 0; i < tableLength; i++) {
              offsets[i] = new TargetInfo.LocalvarTarget.Offsets(data.readUnsignedShort(), data.readUnsignedShort(), data.readUnsignedShort());
            }
            return new TargetInfo.LocalvarTarget(offsets);
          case TypeAnnotation.CATCH_CLAUSE:
            return new TargetInfo.CatchTarget(data.readUnsignedShort());
          case TypeAnnotation.EXPR_INSTANCEOF:case TypeAnnotation.EXPR_NEW:case TypeAnnotation.EXPR_CONSTRUCTOR_REF:case TypeAnnotation.EXPR_METHOD_REF:
            return new TargetInfo.OffsetTarget(data.readUnsignedShort());
          case TypeAnnotation.TYPE_ARG_CAST:case TypeAnnotation.TYPE_ARG_CONSTRUCTOR_CALL:case TypeAnnotation.TYPE_ARG_METHOD_CALL:
          case TypeAnnotation.TYPE_ARG_CONSTRUCTOR_REF:case TypeAnnotation.TYPE_ARG_METHOD_REF:
            return new TargetInfo.TypeArgumentTarget(data.readUnsignedShort(), data.readUnsignedByte());
          default: throw new RuntimeException("unknown target type: " + targetType);
        }
    }

  public List<TypeAnnotation> getAnnotations() {
    return annotations;
  }
}