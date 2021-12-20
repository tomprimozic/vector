Licence: public domain

This is an implementation of a _persistent array_ data structure which can be used as a
general-purpose _random-access_ array in functional programming languages. It is modeled after
Clojure's `Vector`, but supports amortized `O(1)` modification on _both_ ends, and can therefore
be used both as a _stack_ and as a _queue_.

The basic idea is simple - we take 2 Clojure's `Vector`s and join them together; since Clojure's
`Vector` supports efficient modification on _one_ end (the `tail`), we can use 2 of them (joined
together at the `head`) to support efficient modification on _both_ sides.

      [tail  ...   tree] [tree  ...   tail]
        ↑     <- rotceV   Vector ->    ↑
        ┃                              ┃
        ┗━ efficiently `push` / `pop` ━┛

However, the devil is in the details. First of all, in order to enable structural sharing, we the
_direction_ of data on the `Left` and `Right` halves must be the same (so that we can move a subtree
from one side to the other without copying), so we need to implement slightly different code for the
`Left` and `Right` sub-vectors.

Additionally, we maintain the invariant that the depth of the `tree` on the `Left` and `Right` sides
differ for at most 1 - therefore, we have to make sure to rebalance both halves as necessary. This is
fairly straightforward when adding elements (`push`) but can get quite complicated when removing
elements (`pop`):



      [tail  ...   tree]    [tree  ...   tail]
                  /   \       |  \
                 N     N      N   N
               / |   / |     / \
              N  N  N  N    N   N
                           /|   |\
                          N N   N N

If we `pop_left` on the tree above, we'll have to reduce the depth of the `Left` side (otherwise
the root of the tree would have a single child). Then, the depth of the `Left` side will be `1`,
while the depth of the `Right` side will be `3`, breaking the invariant. To maintain the invariant,
we'll have to "steal" _some_ data from the `Right` side. But if we take the whole left subtree of
the `Right` tree (`depth=3`), we will break the invariant in the _other_ direction (`Left.depth=3`
but `Right.depth=1`). Therefore, we have to _split_ the left subtree of the `Right` tree.

Quick benchmark (_miliseconds_ - less is better):

```
                    mean        std        min         max
Clojure
      append:     480.00     270.96        269        1057
         get:    2478.83     228.00       2113        2812
       stack:    2726.67     337.09       2230        3294
      update:    8958.67    1228.49       7749       10796
Scala2
      append:     719.67     195.52        506         991
         get:    1939.33     564.50       1275        2796
       queue:    3754.67     478.91       3189        4575
       stack:    5615.33    1227.48       4267        7940
      update:    7484.83     895.97       6926        9466
Vector
      append:     401.17      41.57        361         489
         get:    1815.17     309.99       1498        2413
       queue:    1637.17     410.76       1208        2317
       stack:    2924.83     422.32       2620        3857
      update:    8403.33    1265.10       6931        9791
```