package ru.ifmo.steady.inds;

public class StorageLastHull2 extends StorageWithConvexHull {

    public String getName() {
        return "INDS-LastHull2";
    }

    /* Internals */

    int currentUpperLimit = 4;

    @Override
    protected LLNode removeWorstByCrowding(int count) {
        int upperLimit = maxLastLayerSize = Math.max(maxLastLayerSize, (int) (Math.sqrt(1. * layerRoot.rightmost().key().size() / layerRoot.size())));
        if (currentUpperLimit * 2 < upperLimit) {
            currentUpperLimit = upperLimit;
            rebuildAll(currentUpperLimit);
        } else {
            rebuildLastLayer(currentUpperLimit);
        }
        return super.removeWorstByCrowding(count);
    }

    private void rebuildAll(int upperLimit) {
        if (layerRoot == null) return;
        LLNode start = layerRoot.rightmost().key().leftmost();
        for (LLNode first = start; first != null; first = first.next()) {
            first.hull = null;
        }
        rebuildLastLayer(upperLimit);
    }

    private void rebuildLastLayer(int upperLimit) {
        upperLimit /= 2;
        if (layerRoot == null) return;
        LLNode start = layerRoot.rightmost().key().leftmost();
        for (LLNode first = start; first != null; ) {
            if (first.hull != null && first.hull.isAlive) {
                first = first.hull.last.next();
                continue;
            }
            LLNode last = first;
            int cnt = 1;
            while (last.next() != null && (last.next().hull == null || !last.next().hull.isAlive)) {
                last = last.next();
                ++cnt;
            }
            if (2 * cnt < upperLimit) {
                LLNode prev = first.prev();
                LLNode next = last.next();
                if (prev != null) {
                    if (prev.hull == null || !prev.hull.isAlive) throw new AssertionError();
                    int prevSize = prev.hull.rangeSize;
                    first = prev.hull.first;
                    cnt += prevSize;
                } else if (next != null) {
                    if (next.hull == null || !next.hull.isAlive) throw new AssertionError();
                    int nextSize = next.hull.rangeSize;
                    last = next.hull.last;
                    cnt += nextSize;
                } else return;
            }
            if (2 * cnt < upperLimit) throw new AssertionError();
            while (cnt > 0) {
                int get = cnt >= 2 * upperLimit ? upperLimit : cnt;
                LLNode cur = first;
                cnt -= get;
                --get;
                while (get > 0) {
                    get--;
                    cur = cur.next();
                }
                new ConvexHull(first, cur);
                first = cur.next();
            }
            if (first != last.next()) throw new AssertionError();
        }
    }
}
