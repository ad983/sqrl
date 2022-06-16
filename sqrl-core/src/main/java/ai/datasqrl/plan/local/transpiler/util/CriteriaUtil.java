package ai.datasqrl.plan.local.transpiler.util;

import static ai.datasqrl.parse.util.SqrlNodeUtil.and;

import ai.datasqrl.parse.tree.ComparisonExpression;
import ai.datasqrl.parse.tree.ComparisonExpression.Operator;
import ai.datasqrl.parse.tree.Expression;
import ai.datasqrl.parse.tree.JoinOn;
import ai.datasqrl.plan.local.transpiler.nodes.expression.ReferenceExpression;
import ai.datasqrl.plan.local.transpiler.nodes.expression.ResolvedColumn;
import ai.datasqrl.plan.local.transpiler.nodes.relation.JoinNorm;
import ai.datasqrl.plan.local.transpiler.nodes.relation.QuerySpecNorm;
import ai.datasqrl.plan.local.transpiler.nodes.relation.RelationNorm;
import ai.datasqrl.plan.local.transpiler.nodes.relation.TableNodeNorm;
import ai.datasqrl.schema.Table;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CriteriaUtil {

  public static Expression sameTableEq(RelationNorm left, RelationNorm right) {
    List<Expression> condition = ((TableNodeNorm)left).getRef().getTable().getPrimaryKeys().stream()
        .map(column->new ComparisonExpression(Operator.EQUAL,
            ResolvedColumn.of(left, column),
            ResolvedColumn.of(right, column)))
        .collect(Collectors.toList());
    if (condition.isEmpty()) {
      System.out.println();
    }

    Preconditions.checkState(!condition.isEmpty(), "Could not build critiera for %s %s", left, right);

    return and(condition);
  }

  public static Optional<JoinOn> subqueryEq(Table baseTable, TableNodeNorm baseRel,
      QuerySpecNorm subquery) {
    List<Expression> criteria = new ArrayList<>();
    for (int i = 0; i < baseTable.getPrimaryKeys().size(); i++) {
      criteria.add(new ComparisonExpression(Operator.EQUAL,
          ResolvedColumn.of(baseRel, baseTable.getPrimaryKeys().get(i)),
          new ReferenceExpression(subquery,
              subquery.getParentPrimaryKeys().get(i))));
    }

    return JoinOn.on(and(criteria));
  }

  public static Optional<JoinOn> joinEq(TableNodeNorm base, JoinNorm expanded) {

    List<Expression> criteria = new ArrayList<>();
    for (int i = 0; i < expanded.getLeftmost().getPrimaryKeys().size(); i++) {
      criteria.add(new ComparisonExpression(Operator.EQUAL,
          ResolvedColumn.of(base, base.getRef().getTable().getPrimaryKeys().get(i)),
          expanded.getLeftmost().getPrimaryKeys().get(i)));
    }
    return JoinOn.on(and(criteria));
  }
}
