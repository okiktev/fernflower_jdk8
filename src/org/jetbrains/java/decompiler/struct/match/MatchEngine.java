// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.match;

import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.IMatchable.MatchProperties;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;

import java.util.*;

public class MatchEngine {
    
    
    @SuppressWarnings("SpellCheckingInspection")
    private static final Map<String, MatchProperties> stat_properties = new HashMap<>();
    static {
        stat_properties.put("type", MatchProperties.STATEMENT_TYPE);
        stat_properties.put("ret", MatchProperties.STATEMENT_RET);
        stat_properties.put("position", MatchProperties.STATEMENT_POSITION);
        stat_properties.put("statsize", MatchProperties.STATEMENT_STATSIZE);
        stat_properties.put("exprsize", MatchProperties.STATEMENT_EXPRSIZE);
        stat_properties.put("iftype", MatchProperties.STATEMENT_IFTYPE);
    }


  @SuppressWarnings("SpellCheckingInspection")
  private static final Map<String, MatchProperties> expr_properties = new HashMap<>();
  static {
      expr_properties.put("type", MatchProperties.EXPRENT_TYPE);
      expr_properties.put("ret", MatchProperties.EXPRENT_RET);
      expr_properties.put("position", MatchProperties.EXPRENT_POSITION);
      expr_properties.put("functype", MatchProperties.EXPRENT_FUNCTYPE);
      expr_properties.put("exittype", MatchProperties.EXPRENT_EXITTYPE);
      expr_properties.put("consttype", MatchProperties.EXPRENT_CONSTTYPE);
      expr_properties.put("constvalue", MatchProperties.EXPRENT_CONSTVALUE);
      expr_properties.put("invclass", MatchProperties.EXPRENT_INVOCATION_CLASS);
      expr_properties.put("signature", MatchProperties.EXPRENT_INVOCATION_SIGNATURE);
      expr_properties.put("parameter", MatchProperties.EXPRENT_INVOCATION_PARAMETER);
      expr_properties.put("index", MatchProperties.EXPRENT_VAR_INDEX);
      expr_properties.put("name", MatchProperties.EXPRENT_FIELD_NAME);
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static final Map<String, StatementType> stat_type = new HashMap<>();
  static {
      stat_type.put("if", StatementType.IF);
      stat_type.put("do", StatementType.DO);
      stat_type.put("switch", StatementType.SWITCH);
      stat_type.put("trycatch", StatementType.TRY_CATCH);
      stat_type.put("basicblock", StatementType.BASIC_BLOCK);
      stat_type.put("sequence", StatementType.SEQUENCE);
  }

  private static final Map<String, Integer> expr_type = new HashMap<>();
  static {
      expr_type.put("array", Exprent.EXPRENT_ARRAY);
      expr_type.put("assignment", Exprent.EXPRENT_ASSIGNMENT);
      expr_type.put("constant", Exprent.EXPRENT_CONST);
      expr_type.put("exit", Exprent.EXPRENT_EXIT);
      expr_type.put("field", Exprent.EXPRENT_FIELD);
      expr_type.put("function", Exprent.EXPRENT_FUNCTION);
      expr_type.put("if", Exprent.EXPRENT_IF);
      expr_type.put("invocation", Exprent.EXPRENT_INVOCATION);
      expr_type.put("monitor", Exprent.EXPRENT_MONITOR);
      expr_type.put("new", Exprent.EXPRENT_NEW);
      expr_type.put("switch", Exprent.EXPRENT_SWITCH);
      expr_type.put("var", Exprent.EXPRENT_VAR);
      expr_type.put("annotation", Exprent.EXPRENT_ANNOTATION);
      expr_type.put("assert", Exprent.EXPRENT_ASSERT);
  }

  private static final Map<String, Integer> expr_func_type = new HashMap<>();
  static {
      expr_func_type.put("eq", FunctionExprent.FUNCTION_EQ);
  }

  private static final Map<String, Integer> expr_exit_type = new HashMap<>();
  static {
      expr_exit_type.put("return", ExitExprent.EXIT_RETURN);
      expr_exit_type.put("throw", ExitExprent.EXIT_THROW);
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static final Map<String, Integer> stat_if_type = new HashMap<>();
  static {
      stat_if_type.put("if", IfStatement.IFTYPE_IF);
      stat_if_type.put("ifelse", IfStatement.IFTYPE_IFELSE);
  }

  private static final Map<String, VarType> expr_const_type = new HashMap<>();
  static {
      expr_const_type.put("null", VarType.VARTYPE_NULL);
      expr_const_type.put("string", VarType.VARTYPE_STRING);
  }

  private final MatchNode rootNode;
  private final Map<String, Object> variables = new HashMap<>();

  public MatchEngine(String description) {
    // each line is a separate statement/expression
    String[] lines = description.split("\n");

    int depth = 0;
    LinkedList<MatchNode> stack = new LinkedList<>();

    for (String line : lines) {
      List<String> properties = new ArrayList<>(Arrays.asList(line.split("\\s+"))); // split on any number of whitespaces
      if (properties.get(0).isEmpty()) {
        properties.remove(0);
      }

      int node_type = "statement".equals(properties.get(0)) ? MatchNode.MATCHNODE_STATEMENT : MatchNode.MATCHNODE_EXPRENT;

      // create new node
      MatchNode matchNode = new MatchNode(node_type);
      for (int i = 1; i < properties.size(); ++i) {
        String[] values = properties.get(i).split(":");

        MatchProperties property = (node_type == MatchNode.MATCHNODE_STATEMENT ? stat_properties : expr_properties).get(values[0]);
        if (property == null) { // unknown property defined
          throw new RuntimeException("Unknown matching property");
        }
        else {
          Object value = null;
          int parameter = 0;

          String strValue = values[1];
          if (values.length == 3) {
            parameter = Integer.parseInt(values[1]);
            strValue = values[2];
          }

          value = getValue(property, strValue, value);

          matchNode.addRule(property, new RuleValue(parameter, value));
        }
      }

      if (stack.isEmpty()) { // first line, root node
        stack.push(matchNode);
      }
      else {
        // return to the correct parent on the stack
        int new_depth = line.lastIndexOf(' ', depth) + 1;
        for (int i = new_depth; i <= depth; ++i) {
          stack.pop();
        }

        // insert new node
        stack.getFirst().addChild(matchNode);
        stack.push(matchNode);

        depth = new_depth;
      }
    }

    this.rootNode = stack.getLast();
  }

  private Object getValue(MatchProperties property, String strValue, Object value) {
      switch (property) {
      case STATEMENT_TYPE: return stat_type.get(strValue);
      case STATEMENT_STATSIZE:case STATEMENT_EXPRSIZE: return Integer.valueOf(strValue);
      case STATEMENT_POSITION:case EXPRENT_POSITION:case EXPRENT_INVOCATION_CLASS:case EXPRENT_INVOCATION_SIGNATURE:case
              EXPRENT_INVOCATION_PARAMETER:case EXPRENT_VAR_INDEX:case EXPRENT_FIELD_NAME:case EXPRENT_CONSTVALUE:case STATEMENT_RET:case
              EXPRENT_RET: return strValue;
      case STATEMENT_IFTYPE: return stat_if_type.get(strValue);
      case EXPRENT_FUNCTYPE: return expr_func_type.get(strValue);
      case EXPRENT_EXITTYPE: return expr_exit_type.get(strValue);
      case EXPRENT_CONSTTYPE: return expr_const_type.get(strValue);
      case EXPRENT_TYPE: return expr_type.get(strValue);
      default: return value;
      }
  }

  public boolean match(IMatchable object) {
    variables.clear();
    return match(this.rootNode, object);
  }

  private boolean match(MatchNode matchNode, IMatchable object) {
    if (!object.match(matchNode, this)) {
      return false;
    }

    int expr_index = 0;
    int stat_index = 0;
    for (MatchNode childNode : matchNode.getChildren()) {
      boolean isStatement = childNode.getType() == MatchNode.MATCHNODE_STATEMENT;

      IMatchable childObject = object.findObject(childNode, isStatement ? stat_index : expr_index);
      if (childObject == null || !match(childNode, childObject)) {
        return false;
      }

      if (isStatement) {
        stat_index++;
      }
      else {
        expr_index++;
      }
    }

    return true;
  }

  public boolean checkAndSetVariableValue(String name, Object value) {
    Object old_value = variables.get(name);
    if (old_value != null) {
      return old_value.equals(value);
    }
    else {
      variables.put(name, value);
      return true;
    }
  }

  public Object getVariableValue(String name) {
    return variables.get(name);
  }
}
