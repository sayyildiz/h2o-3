package hex.grid.filter;


import hex.Model;

import java.util.function.Predicate;

/**
 * Represent higher level of abstraction comparing to {@link java.util.function.Predicate}
 *
 * By using {@link FunctionalInterface} it is possible to define lambda functions of a corresponding type.
 * Note: interface with {@link FunctionalInterface} will not provide full functional support ( e.g. no `compose` and `andThen` methods)
 */
@FunctionalInterface
public interface FilterFunction<MP extends Model.Parameters> extends Predicate<MP>, Activatable<MP>, Resetable {

  default void activate(boolean globalActivate, MP permutation) { }

  /**
   *  For stateless implementations there is no need to do anything.
   *  For stateful ones consider to override default behaviour.
   */
  default void reset() {}
}
