package ru.ifmo.steady.inds2;

import ru.ifmo.steady.Solution;
import ru.ifmo.steady.SolutionStorage;
import ru.ifmo.steady.inds2.TreapNode.*;
import ru.ifmo.steady.util.FastRandom;

import java.util.*;

import static ru.ifmo.steady.inds2.TreapNode.*;

public class StorageWithConvexHull extends SolutionStorage {

    int K = 1;

    public void add(Solution s) {
        LLNode node = new LLNode(s);
        addToLayers(node);
        makeConvexOnLastLayer();
        int sz = size();
        while (K * K < sz) K++;
    }

    private void makeConvexOnLastLayer() {
        if (layerRoot == null) return;
        HLNode lastLayer = layerRoot.rightmost();
        LLNode v = lastLayer.key();
        int count = 0;
        while (count < 2 * K && v != null) {
            if (v.convexHullStorage == null) {
                v = v.next();
            } else {
                v = v.convexHullStorage.right.next();
            }
            ++count;
        }
        if (count >= 2 * K) {
            v = lastLayer.key();
            while (v != null) {
                ConvexHullStorage cHStorage = v.convexHullStorage;
                if (cHStorage != null) {
                    cHStorage.destruct();
                }
                v = v.next();
            }
            LLNode first = lastLayer.key();
            v = first;
            int curCount = 0;
            while (v != null) {
                curCount++;
                if (curCount == K || v.next() == null) {
                    new StupidConvexHull(first, v);
                }
                first = v = v.next();
                curCount = 0;
            }
        }
    }

    public int getLayerCount() {
        return layerRoot == null ? 0 : layerRoot.size();
    }

    public Iterator<Solution> getLayer(final int index) {
        if (index < 0 || index >= getLayerCount()) {
            throw new IllegalArgumentException("No such layer: " + index);
        }
        return new Iterator<Solution>() {
            private LLNode curr = TreapNode.getKth(layerRoot, index).key().leftmost();

            public boolean hasNext() {
                return curr != null;
            }

            public Solution next() {
                if (!hasNext()) {
                    throw new IllegalStateException("No more elements");
                } else {
                    Solution rv = curr.key();
                    curr = curr.next();
                    return rv;
                }
            }
        };
    }

    public String getName() {
        return "INDSCH";
    }

    public Solution removeWorst() {
        LLNode node = removeWorstByCrowding(1);
        return node.key();
    }

    public void removeWorst(int count) {
        removeWorstByCrowding(count);
    }

    public int size() {
        return layerRoot == null ? 0 : layerRoot.totalSize;
    }

    public void clear() {
        layerRoot = null;
    }

    public QueryResult getRandom() {
        if (layerRoot == null) {
            throw new IllegalStateException("empty data structure");
        }
        return getKth(FastRandom.geneticThreadLocal().nextInt(size()));
    }

    public QueryResult getKth(int index) {
        if (index < 0 || index >= size()) {
            throw new IllegalArgumentException("index = " + index + " size = " + size());
        }
        HLNode layer = layerRoot;
        int layerIndex = 0;
        while (true) {
            HLNode ll = layer.left();
            if (ll != null) {
                int llts = ll.totalSize;
                if (index < llts) {
                    layer = ll;
                    continue;
                }
                layerIndex += ll.size();
                index -= llts;
            }
            int lks = layer.key().size();
            if (index < lks) {
                break;
            }
            layerIndex += 1;
            index -= lks;
            layer = layer.right();
        }
        LLNode layerKey = layer.key();
        LLNode llNode = TreapNode.getKth(layerKey, index);
        Solution s = llNode.key();
        if (layerKey.size() <= 2) {
            return new QueryResult(s, Double.POSITIVE_INFINITY, layerIndex);
        } else {
            Solution layerL = layerKey.leftmost().key();
            Solution layerR = layerKey.rightmost().key();
            double crowd = llNode.crowdingDistance(layerL, layerR);
            return new QueryResult(s, crowd, layerIndex);
        }
    }

    /* Internals */

    /**
     * The tree of layers: each element corresponds to a single layer.
     * Ordering: by layer number (implicit key).
     * Used to determine layers.
     */
    private HLNode layerRoot = null;
    /**
     * For the sake of simplicity, everything is single-threaded,
     * so we can bake these in advance.
     */
    private final SplitResult<LLNode> lSplit = new SplitResult<>();
    private final SplitResult<HLNode> hSplit = new SplitResult<>();
    private final LayerWithIndex lwi = new LayerWithIndex();

    private boolean dominates(LLNode layer, final Solution s) {
        LLNode best = null;
        int cx = -1;
        while (layer != null) {
            int cmp = s.compareX(layer.key(), counter);
            if (cmp >= 0) {
                best = layer;
                layer = layer.right();
                cx = cmp;
            } else {
                layer = layer.left();
            }
        }
        if (best == null) {
            return false;
        } else {
            int cy = s.compareY(best.key(), counter);
            return cx == 0 ? cy > 0 : cy >= 0;
        }
    }

    private static final class LayerWithIndex {
        public int index;
        public HLNode layer;
    }

    private LayerWithIndex smallestNonDominatingLayer(Solution s) {
        int index = 0;
        HLNode best = null;
        HLNode curr = layerRoot;
        while (curr != null) {
            HLNode cr = curr.left();
            if (dominates(curr.key(), s)) {
                index += 1;
                if (cr != null) {
                    index += cr.size();
                }
                curr = curr.right();
            } else {
                best = curr;
                curr = cr;
            }
        }
        lwi.layer = best;
        lwi.index = index;
        return lwi;
    }

    private void recomputeInterval(HLNode node, int from, int until) {
        if (node == null) {
            return;
        }
        HLNode left = node.left();
        if (left != null) {
            int ls = left.size();
            if (from < ls) {
                recomputeInterval(left, from, Math.min(until, ls));
            }
            from -= ls;
            until -= ls;
        }
        from -= 1;
        until -= 1;
        HLNode right = node.right();
        if (right != null) {
            int rs = right.size();
            if (until >= 0) {
                recomputeInterval(right, Math.max(0, from), until);
            }
        }
        node.recomputeInternals();
    }

    private void addToLayers(LLNode node) {
        LLNode currPush = node;
        LayerWithIndex currLWI = smallestNonDominatingLayer(node.key());
        HLNode currLayer = currLWI.layer;
        int currIndex = currLWI.index;
        int initIndex = currIndex;
        boolean firstTime = true;
        while (currLayer != null) {
            Solution min = currPush.leftmost().key();
            Solution max = currPush.rightmost().key();
            split(currLayer.key(), t -> min.compareX(t.key(), counter) > 0, lSplit);
            LLNode tL = lSplit.left;
            split(lSplit.right, t -> max.compareY(t.key(), counter) <= 0, lSplit);
            LLNode tM = lSplit.left;
            LLNode tR = lSplit.right;
            if (firstTime && tM != null && tM.key().equals(node.key())) {
                currPush = merge(currPush, tM);
                tM = null;
            }
            firstTime = false;
            currLayer.setKey(merge(tL, merge(currPush, tR)));
            if (tM == null) {
                recomputeInterval(layerRoot, initIndex, currIndex + 1);
                return;
            }
            if (tL == null && tR == null) {
                recomputeInterval(layerRoot, initIndex, currIndex + 1);
                splitK(layerRoot, currIndex + 1, hSplit);
                layerRoot = merge(hSplit.left, merge(new HLNode(tM), hSplit.right));
                return;
            }
            currPush = tM;
            currLayer = currLayer.next();
            ++currIndex;
        }
        recomputeInterval(layerRoot, initIndex, currIndex);
        currLayer = new HLNode(currPush);
        layerRoot = merge(layerRoot, currLayer);
    }

    public void removeWorstDebCompatible(int count) {
        if (size() < count) {
            throw new IllegalStateException("Insufficient size of data structure");
        }
        int expected = size() - count;
        HLNode lastLayer = layerRoot.rightmost();
        while (lastLayer.key().size() <= count) {
            count -= lastLayer.key().size();
            cutRightmost(layerRoot, hSplit);
            layerRoot = hSplit.left;
            lastLayer = layerRoot.rightmost();
        }
        if (count > 0) {
            LLNode root = lastLayer.key();
            LLNode min = root.leftmost();
            Solution minS = min.key();
            LLNode max = root.rightmost();
            Solution maxS = max.key();
            int lls = root.size();
            double[] crowding = new double[lls];
            Integer[] indices = new Integer[lls];
            LLNode curr = min;
            int index = 0;
            while (curr != null) {
                indices[index] = index;
                crowding[index] = curr.crowdingDistance(minS, maxS);
                ++index;
                curr = curr.next();
            }
            Arrays.sort(indices, (l, r) -> Double.compare(crowding[r], crowding[l]));
            int remain = lls - count;
            Arrays.sort(indices, 0, remain);
            LLNode newLayer = null;
            for (int i = 0, j = 0; j < remain; ++i) {
                splitK(root, 1, lSplit);
                root = lSplit.right;
                if (indices[j] == i) {
                    newLayer = merge(newLayer, lSplit.left);
                    ++j;
                }
            }
            lastLayer.setKey(newLayer);
            int sz = layerRoot.size();
            recomputeInterval(layerRoot, sz - 1, sz);
        }
        if (size() != expected) {
            throw new AssertionError();
        }
    }

    private LLNode removeOneWorstByCrowding() {
        HLNode lastLayer = layerRoot.rightmost();
        LLNode lastLayerRoot = lastLayer.key();
        if (lastLayerRoot.size() == 1) {
            cutRightmost(layerRoot, hSplit);
            layerRoot = hSplit.left;
            return lastLayer.key();
        } else if (lastLayerRoot.size() == 2) { // TODO, no random
            splitK(lastLayerRoot, 1, lSplit);
            lastLayer.setKey(lSplit.left);
            int rcIndex = layerRoot.size() - 1;
            recomputeInterval(layerRoot, rcIndex, rcIndex + 1);
            return lSplit.right;
        }
        LLNode leftmost = lastLayerRoot.leftmost();
        LLNode rightmost = lastLayerRoot.rightmost();
        double dx = 1. / (rightmost.key().getNormalizedX(0, 1) - leftmost.key().getNormalizedX(0, 1));
        double dy = 1. / (leftmost.key().getNormalizedY(0, 1) - rightmost.key().getNormalizedY(0, 1));
        LLNode chosen = null;
        double bestValue = Double.POSITIVE_INFINITY;
        for (LLNode v = leftmost; v != null; ) {
            LLNode cur;
            if (v.convexHullStorage == null) {
                cur = v;
                v = v.next();
            } else {
                cur = v.convexHullStorage.findBest(dx, dy); // can't do random here
                v = v.convexHullStorage.right.next();
            }
            double curValue = cur.getCrowdingX() * dx + cur.getCrowdingY() * dy;
            if (bestValue > curValue) {
                chosen = cur;
                bestValue = curValue;
            }
        }
        final Solution chosenKey = chosen.key();
        split(lastLayerRoot, lln -> lln.key().compareX(chosenKey, counter) < 0, lSplit);
        LLNode left = lSplit.left;
        splitK(lSplit.right, 1, lSplit);
        LLNode rv = lSplit.left;
        LLNode right = lSplit.right;
        LLNode newLayer = merge(left, right);
        if (newLayer == null) {
            throw new AssertionError("This layer should be non-empty but it isn't");
        }
        lastLayer.setKey(newLayer);
        int rcIndex = layerRoot.size() - 1;
        recomputeInterval(layerRoot, rcIndex, rcIndex + 1);
        return rv;
    }

    private LLNode removeWorstByCrowding(int count) {
        if (size() < count) {
            throw new IllegalStateException("Insufficient size of data structure");
        }
        HLNode lastLayer = layerRoot.rightmost();
        while (lastLayer.key().size() < count) {
            count -= lastLayer.key().size();
            cutRightmost(layerRoot, hSplit);
            layerRoot = hSplit.left;
            lastLayer = layerRoot.rightmost();
        }
        LLNode last = null;
        while (count-- > 0) {
            last = removeOneWorstByCrowding();
        }
        makeConvexOnLastLayer();
        return last;
    }

    /* Node classes */

    private final LLNode[] LLNODE_ZERO_ARRAY = new LLNode[0];

    private final class LLNode extends TreapNode<Solution, LLNode> {
        private CrowdingPoint p = new CrowdingPoint(0, 0);
        public ConvexHullStorage convexHullStorage = null;

        public LLNode(Solution key) {
            super(key);
        }

        public double crowdingDistance(Solution leftmost, Solution rightmost) {
            LLNode prev = prev();
            LLNode next = next();
            return key().crowdingDistance(
                    prev == null ? null : prev.key(),
                    next == null ? null : next.key(),
                    leftmost, rightmost, counter
            );
        }

        public Iterator<LLNode> nextLinkIterator() {
            return new Iterator<LLNode>() {
                private LLNode curr = LLNode.this;

                public boolean hasNext() {
                    return curr != null;
                }

                public LLNode next() {
                    LLNode rv = curr;
                    curr = curr.next();
                    return rv;
                }
            };
        }

        public void changeCrowdingPoint() {
            LLNode prev = prev();
            LLNode next = next();
            if (prev != null && next != null) {
                p.x = next.key().getNormalizedX(0, 1) - prev.key().getNormalizedX(0, 1);
                p.y = prev.key().getNormalizedY(0, 1) - next.key().getNormalizedY(0, 1);
            } else {
                p.x = p.y = Double.POSITIVE_INFINITY;
            }
            if (convexHullStorage != null) {
                convexHullStorage.destruct();
            }
        }

        public double getCrowdingX() {
            return p.x;
        }

        public double getCrowdingY() {
            return p.y;
        }

        protected void setPrev(LLNode prev) {
            super.setPrev(prev);
            changeCrowdingPoint();
        }

        protected void setNext(LLNode next) {
            super.setNext(next);
            changeCrowdingPoint();
        }

    }

    private final class HLNode extends TreapNode<LLNode, HLNode> {
        int totalSize;

        public HLNode(LLNode key) {
            super(key);
        }

        @Override
        public final void recomputeInternals() {
            super.recomputeInternals();
            totalSize = key().size();
            HLNode left = left(), right = right();
            if (left != null) {
                totalSize += left.totalSize;
            }
            if (right != null) {
                totalSize += right.totalSize;
            }
        }
    }


    abstract class ConvexHullStorage {
        final LLNode left;
        final LLNode right;

        protected ConvexHullStorage(LLNode left, LLNode right) {
            this.left = left;
            this.right = right;
        }

        abstract LLNode findBest(double dx, double dy);

        abstract void destruct();
    }

    private class StupidConvexHull extends ConvexHullStorage {
        LLNode[] hull;

        public StupidConvexHull(LLNode left, LLNode right) {
            super(left, right);
            List<LLNode> list = new ArrayList<>();
            while (true) {
                left.convexHullStorage = this;
                list.add(left);
                if (left == right) break;
                left = left.next();
            }
            hull = list.toArray(new LLNode[list.size()]);
        }


        @Override
        LLNode findBest(double dx, double dy) {
            double bestCrowding = Double.POSITIVE_INFINITY;
            LLNode best = null;
            for (LLNode f : hull) {
                double curCrowding = dx * f.getCrowdingX() + dy * f.getCrowdingY();
                if (best == null || bestCrowding > curCrowding) {
                    best = f;
                    bestCrowding = curCrowding;
                }
            }
            return best;
        }

        @Override
        void destruct() {
            LLNode left = this.left;
            while (true) {
                left.convexHullStorage = null;
                if (left == right) break;
                left = left.next();
            }
        }
    }
}
