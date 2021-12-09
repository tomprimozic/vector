package experiment.vector;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Arrays;


public class VectorExplained<T> {
  private final Object[] EMPTY_ARRAY =  new Object[]{};

  public final int SHIFT;
  public final int SIZE;      // size of a trie level
  public final int MASK;

  public final Left left;
  public final Right right;

  private VectorExplained(int SHIFT, Left left, Right right) {
    this.SHIFT = SHIFT;
    this.SIZE = 1 << SHIFT;
    this.MASK = SIZE - 1;
    this.left = left;
    this.right = right;

    assert SHIFT >= 1;
    assert Math.abs(left.depth() - right.depth()) <= 1;
  }
  private VectorExplained(int SHIFT) {
    this.SHIFT = SHIFT;
    this.SIZE = 1 << SHIFT;
    this.MASK = SIZE - 1;
    this.left = new Left(0, null, EMPTY_ARRAY);
    this.right = new Right(0, null, EMPTY_ARRAY);

    assert SHIFT >= 1;
  }

  public static <T> VectorExplained<T> empty(int SHIFT) { return new VectorExplained<>(SHIFT); }
  public void debug() { left.debug(); right.debug(); }
  public int size() { return left.size() + right.size(); }
  public T get(int i) {
    if(!(0 <= i && i < size())) { throw new IndexOutOfBoundsException(i); }
    return i < left.size() ? left.get(i) : right.get(i - left.size());
  }
  public int shift(int data_size) {
    int rem = data_size - 1;
    int shift = 0;
    while(rem > 0) {
      rem = rem >> SHIFT;
      shift += SHIFT;
    }
    return shift;
  }

  public java.util.List<T> toList() {
    var length = size();
    var result = new java.util.ArrayList<T>(length);
    for(var i = 0; i < size(); i++) {
      result.add(get(i));
    }
    return result;
  }

  private <A extends Half<A>, B extends Half<B>> ImmutablePair<A, B> push(T e, A other, B to) {
    if(other.depth() < to.depth() && to.extra.length == SIZE && to.data_size >= (1 << to.shift())) {
      assert other.depth() + 1 == to.depth();
      assert to.data.array.length == SIZE;
      // instead of creating a new level in `to`, create a new level in `other` and move half of the `to` elements into it
      if(other.data == null) {
        assert to.depth() == 1;
        assert to.data_size == SIZE;
        return new ImmutablePair<>(other.make(SIZE, to.data, other.extra), to.make(SIZE, new Node(1, to.extra), new Object[]{e}));
      } else {
        var split = to.split(
            other.data,
            to.data.array,
            single(to.depth() - 1, to.extra)
        );
        return new ImmutablePair<>(
            other.make(other.data_size + to.data_size / 2, new Node(other.depth() + 1, split.left), other.extra),
            to.make(to.data_size / 2 + SIZE, new Node(to.depth(), split.right), new Object[]{e})
        );
      }
    } else {
      return new ImmutablePair<>(other, to.push(e));
    }
  }

  public VectorExplained<T> push_right(T e) {
    var result = push(e, left, right);
    return new VectorExplained<>(SHIFT, result.left, result.right);
  }
  public VectorExplained<T> push_left(T e) {
    var result = push(e, right, left);
    return new VectorExplained<>(SHIFT, result.right, result.left);
  }

  private <A extends Half<A>, B extends Half<B>> ImmutablePair<A, B> pop(A other, B from) {
    if(from.extra.length <= 1) {         // will change `data`
      if (other.depth() > from.depth()) {   // need to take care of maintaining invariant
        if(from.size() == 0) {      // we're popping from an empty half, take data from the other half
          assert from.depth() == 0;
          assert other.data_size > 0;
          assert other.depth() == 1;
          return new ImmutablePair<>(
              other.make(0, null, other.extra),
              from.make(0, null, from.pop(other.data.array))
          );
        } else if(from.data_size == SIZE || from.data_size == (1 << (from.shift() - SHIFT)) + SIZE) {
          // would reduce `from.depth` and break the invariant
          assert other.depth() - 1 == from.depth();
          Node other_data, from_data;
          Object[] from_extra = from.depth() == 1 ? from.data.array : from.end();
          if(from.extra.length == 0) {
            from_extra = from.pop(from_extra);
          }
          int moved;
          if(other.data.array.length == 2 && other.data.get(other.end_index(other.data.array)).array.length == 1) {
            // naively moving data from `other` to `from` would break the invariant in the *other* direction
            assert other.depth() >= 3;
            assert from.depth() > 1;
            assert from.data.array.length == 2;
            moved = (1 << (other.shift() - SHIFT)) / 2;
            var split = from.split(
                other.data.get(other.end_index(other.data.array)).array[0],
                other.data.get(other.start_index(other.data.array)).array,
                from.data.array[from.start_index(from.data.array)]
            );
            other_data = new Node(other.depth() - 1, split.left);
            from_data = new Node(from.depth(), split.right);
          } else {
            // move last element of `other.data` to `from.data` to maintain the invariant
            if(other.data.array.length == 2) {
              other_data = other.data.get(other.end_index(other.data.array));
            } else {
              other_data = new Node(other.depth(), from.pop(other.data.array));
            }
            moved = 1 << (other.shift() - SHIFT);
            if (from.depth() == 1) {
              assert from.data_size == SIZE;
              assert from.data.array.length == SIZE;
              from_data = other.data.get(other.start_index(other.data.array));
            } else {
              assert from.depth() > 1;
              assert from.data.array.length == 2;
              from_data = from.array2(
                  other.data.array[other.start_index(other.data.array)],
                  new Node(from.depth(), new Object[]{from.data.array[from.start_index(from.data.array)]})
              );
            }
          }
          return new ImmutablePair<>(
              other.make(other.data_size - moved, other_data, other.extra),
              from.make(from.data_size + moved - SIZE, from_data, from_extra)
          );
        }
      } else if(from.extra.length == 0 && other.depth() == 0 && from.depth() == 0) {
        if(other.extra.length > 0) {
          return new ImmutablePair<>(
              other.make(0, null, EMPTY_ARRAY),
              from.make(0, null, from.pop(other.extra))
          );
        } else {
          assert other.size() == 0;
          throw new IllegalStateException("empty vector");
        }
      }
    }
    return new ImmutablePair<>(other, from.pop());
  }

  public VectorExplained<T> pop_right() {
    var result = pop(left, right);
    return new VectorExplained<>(SHIFT, result.left, result.right);
  }
  public VectorExplained<T> pop_left() {
    var result = pop(right, left);
    return new VectorExplained<>(SHIFT, result.right, result.left);
  }

  public class Node {
    public final Object[] array;
    public final int depth;

    public Node(int depth, Object[] array) {
      this.array = array;
      this.depth = depth;

      assert depth >= 1;
      assert 1 <= array.length && array.length <= SIZE;
      if(depth == 1) {
        assert array.length == SIZE;
        assert Arrays.stream(array).noneMatch(e -> e instanceof VectorExplained<?>.Node);
      } else {
        assert Arrays.stream(array).allMatch(e -> e instanceof VectorExplained<?>.Node && ((VectorExplained<?>.Node) e).depth == depth - 1);
      }
    }

    public int size() {
      return depth == 1 ? array.length : Arrays.stream(array).mapToInt(e -> ((VectorExplained<?>.Node) e).size()).sum();
    }

    public void debug(int indent) {
      if(depth == 1) {
        System.out.print("  ".repeat(indent));
        System.out.println(Arrays.toString(array));
      } else {
        System.out.println("  ".repeat(indent) + "[");
        for (var n : array) {
          ((VectorExplained<?>.Node) n).debug(indent + 1);
        }
        System.out.println("  ".repeat(indent) + "]");
      }
    }

    public Node get(int i) { return (Node) array[i]; }
  }

  public VectorExplained<?>.Node single(int depth, Object[] array) {
    assert depth >= 1;
    return new VectorExplained<?>.Node(depth, depth == 1 ? array : new Object[] {single(depth - 1, array)});
  }

  abstract public class Half<This extends Half<This>> {
    public final Node data;
    public final Object[] extra;
    public final int data_size;

    protected Half(int data_size, Node data, Object[] extra) {
      this.data_size = data_size;
      this.data = data;
      this.extra = extra;

      assert extra != null;
      assert extra.length <= SIZE;
      assert data_size % SIZE == 0;
      if(data != null) {
        assert data.array.length > 1;
        assert data_size == data.size();
        assert depth() == data.depth;
      } else {
        assert data_size == 0;
        assert depth() == 0;
      }
    }

    public int shift() { return VectorExplained.this.shift(data_size); }
    public int depth() { return shift() / SHIFT; }
    public int size() { return data_size + extra.length; }

    abstract public void debug();
    abstract public T get(int i);
    abstract public This make(int data_size, Node data, Object[] extra);
    abstract public Object[] push(Object[] array, Object element);
    abstract public Object[] pop(Object[] array);
    abstract public int start_index(Object[] array);
    abstract public int end_index(Object[] array);
    abstract public Object[] end();

    /** Splits array into two halves, with one additional slot at each end. */
    public ImmutablePair<Object[], Object[]> split(Object[] array) {
      assert array.length == SIZE;
      var start = new Object[SIZE / 2 + 1];
      System.arraycopy(array, 0, start, 1, SIZE / 2);
      var end = new Object[SIZE / 2 + 1];
      System.arraycopy(array, SIZE / 2, end, 0, SIZE / 2);
      return new ImmutablePair<>(start, end);
    }

    /** Splits array into two halves, inserts `start` at the start of the first
     half and `end` at the end of the second half. */
    public ImmutablePair<Object[], Object[]> split(Object start, Object[] array, Object end) {
      var split = this.split(array);
      var start_data = split.left;
      start_data[this.start_index(start_data)] = start;
      var end_data = split.right;
      end_data[this.end_index(end_data)] = end;
      return new ImmutablePair<>(start_data, end_data);
    }

    public Node array2(Object start, Object end) {
      Object[] array = new Object[2];
      array[start_index(array)] = start;
      array[end_index(array)] = end;
      return new Node(depth() + 1, array);
    }

    /** adds an element to the end of this half */
    public This push(T e) {
      if(extra.length < SIZE) {
        return make(data_size, data, push(extra, e));
      } else {
        assert extra.length == SIZE;
        assert data_size % SIZE == 0;
        Node new_data;
        if(data == null) {
          new_data = new Node(1, extra);
        } else if(data_size >= 1 << shift()) {    // need new level
          assert data_size == 1 << shift();
          new_data = array2(data, single(depth(), extra));
        } else {
          new_data = push_node(data, shift() - SHIFT, extra);
        }
        return make(data_size + SIZE, new_data, new Object[]{e});
      }
    }

    public Node push_node(Node parent, int shift, Object[] array) {
      assert shift >= 0;
      if(shift == 0) { return new Node(1, array); }
      int depth = shift / SHIFT;
      assert data_size % SIZE == 0;
      int i = (data_size >> shift) & MASK;
      if(i < parent.array.length) {
        assert i == parent.array.length - 1;
        Object[] updated = parent.array.clone();
        int j = end_index(updated);
        updated[j] = push_node(parent.get(j), shift - SHIFT, array);
        return new Node(depth + 1, updated);
      } else {
        assert i == parent.array.length;
        return new Node(depth + 1, push(parent.array, single(depth, array)));
      }
    }

    /** removes the last element */
    public This pop() {
      if(extra.length > 1) {
        return make(data_size, data, pop(extra));
      } else if(data == null) {
        if(extra.length == 0) { throw new IllegalStateException("empty"); }
        return make(0, null, EMPTY_ARRAY);
      } else {
        Object[] new_extra;
        Node new_data;
        if(data_size == SIZE) {
          assert depth() == 1;
          new_extra = data.array;
          new_data = null;
        } else {
          assert depth() > 1;
          new_extra = end();
          if (data_size == (1 << (shift() - SHIFT)) + SIZE) {  // reduce depth
            assert data.array.length == 2;
            new_data = data.get(start_index(data.array));
          } else {
            int depth = depth();
            new_data = new Node(depth, pop(data, shift()));
          }
        }
        assert new_extra.length == SIZE;
        if(extra.length == 0) {
          new_extra = pop(new_extra);
        }
        return make(data_size - SIZE, new_data, new_extra);
      }
    }

    public Object[] pop(Node parent, int shift) {
      assert data != null;
      assert depth() > 1;
      assert shift > SHIFT;
      shift -= SHIFT;
      int mask = (1 << shift) - SIZE;         // selects all *next* indices
      if(((data_size - 1) & mask) == 0) {     // all next indices are 0 -> remove whole column
        assert parent.array.length > 1;
        return pop(parent.array);
      } else {
        assert parent.depth > 1;
        Object[] updated = parent.array.clone();
        int i = end_index(updated);
        updated[i] = new Node(parent.depth - 1, pop((Node) updated[i], shift));
        return updated;
      }
    }
  }

  public final class Right extends Half<Right> {
    Right(int data_size, Node data, Object[] extra) {
      super(data_size, data, extra);
    }

    public Right make(int data_size, Node data, Object[] extra) { return new Right(data_size, data, extra); }
    public int start_index(Object[] array) { return 0; }
    public int end_index(Object[] array) { return array.length - 1; }
    public Object[] end() { return get_array(data_size - 1); }

    public Object[] push(Object[] array, Object element) {
      Object[] result = new Object[array.length + 1];
      System.arraycopy(array, 0, result, 0, array.length);
      result[array.length] = element;
      return result;
    }
    public Object[] pop(Object[] array) {
      Object[] result = new Object[array.length - 1];
      System.arraycopy(array, 0, result, 0, result.length);
      return result;
    }

    public void debug() {
      System.out.println("Right: size=" + size() + " depth=" + depth() + " shift=" + shift());
      if(data == null) {
        System.out.println("  []");
      } else {
        data.debug(1);
      }
      System.out.println("  " + Arrays.toString(extra));
    }

    @SuppressWarnings("unchecked")
    public T get(int i) {
      if(i >= data_size) { return (T) extra[i - data_size]; }
      Object[] a = get_array(i);
      return (T) a[i & MASK];
    }

    public Object[] get_array(int i) {
      int shift = this.shift();
      Node n = data;
      while(shift > SHIFT) {
        shift -= SHIFT;
        n = n.get((i >> shift) & MASK);
      }
      return n.array;
    }
  }

  public final class Left extends Half<Left> {
    Left(int data_size, Node data, Object[] extra) {
      super(data_size, data, extra);
    }

    public Left make(int data_size, Node data, Object[] extra) { return new Left(data_size, data, extra); }
    public int start_index(Object[] array) { return array.length - 1; }
    public int end_index(Object[] array) { return 0; }
    public Object[] end() { return get_array(0); }

    public Object[] push(Object[] array, Object element) {
      Object[] result = new Object[array.length + 1];
      System.arraycopy(array, 0, result, 1, array.length);
      result[0] = element;
      return result;
    }
    public Object[] pop(Object[] array) {
      Object[] result = new Object[array.length - 1];
      System.arraycopy(array, 1, result, 0, result.length);
      return result;
    }
    public ImmutablePair<Object[], Object[]> split(Object[] array) {
      var pair = super.split(array);
      return new ImmutablePair<>(pair.right, pair.left);
    }

    public void debug() {
      System.out.println("Left: size=" + size() + " depth=" + depth() + " shift=" + shift());
      System.out.println("  " + Arrays.toString(extra));
      if(data == null) {
        System.out.println("  []");
      } else {
        data.debug(1);
      }
    }

    @SuppressWarnings("unchecked")
    public T get(int i) {
      if(i < extra.length) { return (T) extra[i]; }
      i -= extra.length;
      Object[] a = get_array(i);
      return (T) a[i & MASK];
    }

    public Object[] get_array(int i) {
      i += ((1 << shift()) - data_size);    // adjust because of right bias
      int shift = this.shift();
      Node n = data;
      while(shift > SHIFT) {
        shift -= SHIFT;
        n = n.get(((i >> shift) & MASK) - SIZE + n.array.length);
      }
      return n.array;
    }
  }
}

