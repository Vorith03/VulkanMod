package net.vulkanmod.render.chunk.util;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Consumer;

public class ResettableQueue<T> implements Iterable<T> {
    T[] queue;
    int position = 0;
    int limit = 0;
    int capacity;

    private int previousLimit = -1;
    private boolean rewriteMatches;

    public ResettableQueue() {
        this(1024);
    }

    @SuppressWarnings("unchecked")
    public ResettableQueue(int initialCapacity) {
        this.capacity = initialCapacity;

        this.queue = (T[])(new Object[capacity]);
    }

    public boolean hasNext() {
        return this.position < this.limit;
    }

    public T poll() {
        T t = this.queue[position];
        this.position++;

        return t;
    }

    public void add(T t) {
        if(t == null)
            return;

        if(limit == capacity) resize();

        if(this.previousLimit >= 0 && this.rewriteMatches
                && (this.limit >= this.previousLimit || this.queue[this.limit] != t)) {
            this.rewriteMatches = false;
        }

        this.queue[limit] = t;

        this.limit++;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        this.capacity *= 2;

        T[] oldQueue = this.queue;
        this.queue = (T[])(new Object[capacity]);

        System.arraycopy(oldQueue, 0, this.queue, 0, oldQueue.length);
    }

    public int size() {
        return limit;
    }

    public void clear() {
        this.position = 0;
        this.limit = 0;
        this.previousLimit = -1;
        this.rewriteMatches = false;
    }

    /**
     * Rebuild this queue while retaining the old references long enough to compare
     * the new ordered contents without allocating a second collection.
     */
    public void beginRewrite() {
        if(this.previousLimit >= 0)
            throw new IllegalStateException("Queue rewrite already active");

        this.previousLimit = this.limit;
        this.rewriteMatches = true;
        this.position = 0;
        this.limit = 0;
    }

    /**
     * Finish a beginRewrite/add sequence.
     *
     * @return true when the ordered queue contents are reference-identical to the
     * previous contents, including length.
     */
    public boolean endRewrite() {
        if(this.previousLimit < 0)
            return true;

        boolean unchanged = this.rewriteMatches && this.limit == this.previousLimit;
        this.previousLimit = -1;
        this.rewriteMatches = false;
        return unchanged;
    }

    public Iterator<T> iterator(boolean reverseOrder) {
        return reverseOrder ? new Iterator<>() {
            int pos = ResettableQueue.this.limit - 1;
            final int limit = -1;

            @Override
            public boolean hasNext() {
                return pos > limit;
            }

            @Override
            public T next() {
                return queue[pos--];
            }
        }
                : new Iterator<>() {
            int pos = 0;
            final int limit = ResettableQueue.this.limit;

            @Override
            public boolean hasNext() {
                return pos < limit;
            }

            @Override
            public T next() {
                return queue[pos++];
            }
        };
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return iterator(false);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for(int i = 0; i < this.limit; ++i) {
            action.accept(this.queue[i]);
        }

    }
}
