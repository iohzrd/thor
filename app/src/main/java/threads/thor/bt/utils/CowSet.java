package threads.thor.bt.utils;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class CowSet<E> implements Set<E> {

    private static final AtomicReferenceFieldUpdater<CowSet, HashMap> u =
            AtomicReferenceFieldUpdater.newUpdater(CowSet.class, HashMap.class, "backingStore");

    private volatile HashMap<E, Boolean> backingStore = new HashMap<>();

    private <T> T update(Function<HashMap<E, Boolean>, ? extends T> c) {
        HashMap<E, Boolean> current;
        final HashMap<E, Boolean> newMap = new HashMap<>();
        T ret;

        do {
            current = u.get(this);
            newMap.clear();
            newMap.putAll(current);
            ret = c.apply(newMap);
        } while (!u.compareAndSet(this, current, newMap));

        return ret;
    }


    @Override
    public int size() {
        return backingStore.size();
    }

    @Override
    public boolean isEmpty() {
        return backingStore.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return backingStore.containsKey(o);
    }

    @NonNull
    @Override
    public Iterator<E> iterator() {
        return Collections.unmodifiableCollection(backingStore.keySet()).iterator();
    }

    @NonNull
    @Override
    public Object[] toArray() {
        return backingStore.keySet().toArray();
    }

    @NonNull
    @Override
    public <T> T[] toArray(@NonNull T[] a) {
        return backingStore.keySet().toArray(a);
    }

    @Override
    public boolean add(E e) {
        if (backingStore.containsKey(e))
            return false;
        return update(m -> m.putIfAbsent(e, Boolean.TRUE) == null);
    }

    @Override
    public boolean remove(Object o) {
        if (!backingStore.containsKey(o))
            return false;
        return update(m -> m.keySet().remove(o));
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> c) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends E> c) {
        return update(m -> {
            boolean added = false;
            for (E e : c) {
                added |= m.put(e, Boolean.TRUE) == null;
            }
            return added;
        });
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> c) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> c) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void clear() {
        backingStore = new HashMap<>();
    }

    @NonNull
    @Override
    public Stream<E> stream() {
        return backingStore.keySet().stream();
    }

    @Override
    public boolean removeIf(@NonNull Predicate<? super E> filter) {
        return update(m -> m.keySet().removeIf(filter));
    }

    public Set<E> snapshot() {
        return Collections.unmodifiableSet(backingStore.keySet());
    }

}
