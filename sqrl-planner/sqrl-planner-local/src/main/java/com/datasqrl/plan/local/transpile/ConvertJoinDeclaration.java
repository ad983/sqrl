/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.plan.local.transpile;

import com.datasqrl.canonicalizer.ReservedName;
import com.datasqrl.plan.table.VirtualRelationalTable;
import com.google.common.base.Preconditions;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.util.SqlShuttle;
import org.apache.calcite.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Convert the join declaration to a normal select
 */
public class ConvertJoinDeclaration extends SqlShuttle {

  private final Optional<VirtualRelationalTable> context;

  public ConvertJoinDeclaration(Optional<VirtualRelationalTable> context) {
    this.context = context;
  }

  @Override
  public SqlNode visit(SqlCall call) {

    switch (call.getKind()) {
      case JOIN_DECLARATION:
        return convertToSelect((SqrlJoinDeclarationSpec) call);

    }

    return super.visit(call);
  }

  private SqlNode convertToSelect(SqrlJoinDeclarationSpec node) {
    //todo UNION
    SqrlJoinPath path = (SqrlJoinPath) node.getRelation();

    SqlNode from;
    SqlNode where;
    if (path.relations.size() == 1) {
      from = path.relations.get(0);
      where = path.conditions.get(0);
    } else {
      from = convertToBushyTree(path.getRelations(), path.getConditions());
      where = null;
    }

    from = addLeftJoins(from, node.getLeftJoins());

    List<SqlNode> projects = new ArrayList<>();
    //add ppk
    if (context.isPresent()) {
      int cnt = 0;
      for (String pkName : context.get().getPrimaryKeyNames()) {
        SqlIdentifier i = new SqlIdentifier(
            List.of(ReservedName.SELF_IDENTIFIER.getCanonical(), pkName), SqlParserPos.ZERO);
        SqlIdentifier name = new SqlIdentifier(List.of("_ppk_" + Integer.toString(++cnt)),
            SqlParserPos.ZERO);
        SqlCall alias = SqlStdOperatorTable.AS.createCall(SqlParserPos.ZERO, i, name);
        projects.add(alias);
      }
    }

    SqlNode star = SqlIdentifier.star(
        List.of(getAliasName(path.relations.get(path.relations.size() - 1)), "*"),
        SqlParserPos.ZERO, List.of(SqlParserPos.ZERO, SqlParserPos.ZERO));
    projects.add(star);
    SqlSelect select = new SqlSelect(SqlParserPos.ZERO,
        new SqlNodeList(SqlParserPos.ZERO),
        new SqlNodeList(projects, SqlParserPos.ZERO),
        from,
        where,
        null,
        null,
        new SqlNodeList(SqlParserPos.ZERO),
        node.orderList.orElse(null),
        null,
        node.fetch.orElse(null),
        new SqlNodeList(SqlParserPos.ZERO)
    );
    return select;
  }

  public static String getAliasName(SqlNode sqlNode) {
    switch (sqlNode.getKind()) {
      case AS:
        SqlCall call = (SqlCall) sqlNode;
        return Util.last(((SqlIdentifier) call.getOperandList().get(1)).names);
      case JOIN:
        SqlJoin join = (SqlJoin) sqlNode;
        return getAliasName(join.getRight());
    }
    throw new RuntimeException("Could not find alias name");
  }

  private SqlNode addLeftJoins(SqlNode from, Optional<SqlNodeList> leftJoins) {
    if (leftJoins.isEmpty()) {
      return from;
    }
    for (SqlNode node : leftJoins.get()) {
      UnboundJoin u = (UnboundJoin) node;
      from = new SqlJoin(u.getRelation().getParserPosition(),
          from,
          SqlLiteral.createBoolean(false, SqlParserPos.ZERO),
          JoinType.LEFT.symbol(SqlParserPos.ZERO),
          u.getRelation(),
          JoinConditionType.ON.symbol(SqlParserPos.ZERO),
          u.getCondition().orElse(SqlLiteral.createBoolean(true, SqlParserPos.ZERO))
      );

    }

    return from;
  }

  public static SqlNode convertToBushyTree(List<SqlNode> relations, List<SqlNode> conditions) {
    Preconditions.checkState(conditions.get(0) == null);
    SqlJoin join = new SqlJoin(relations.get(0).getParserPosition(),
        relations.get(0),
        SqlLiteral.createBoolean(false, SqlParserPos.ZERO),
        JoinType.DEFAULT.symbol(SqlParserPos.ZERO),
        relations.get(1),
        JoinConditionType.ON.symbol(SqlParserPos.ZERO),
        conditions.get(1) //todo join both
    );
    for (int i = 2; i < relations.size(); i++) {
      join = new SqlJoin(relations.get(0).getParserPosition(),
          join,
          SqlLiteral.createBoolean(false, SqlParserPos.ZERO),
          JoinType.DEFAULT.symbol(SqlParserPos.ZERO),
          relations.get(i),
          JoinConditionType.ON.symbol(SqlParserPos.ZERO),
          conditions.get(i)
      );
    }

    return join;
  }
}
