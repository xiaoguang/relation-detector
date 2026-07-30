package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * CN: 在固定宽度磁盘parent数组上执行确定性union-find，较大root总是连接到较小root；输入dense dictionary id，
 * 输出component root，不在堆中保存与table数量线性增长的数组。
 * EN: Performs deterministic union-find over a fixed-width on-disk parent array, always attaching the larger root
 * to the smaller root. It consumes dense dictionary ids without a heap array proportional to table count.
 */
final class DiskUnionFind implements AutoCloseable {
    private static final int WIDTH = Integer.BYTES;
    private final RandomAccessFile parents;
    private final int size;

    DiskUnionFind(Path path, int size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("disk union-find size must not be negative");
        }
        this.size = size;
        parents = new RandomAccessFile(path.toFile(), "rw");
        parents.setLength((long) size * WIDTH);
        for (int id = 0; id < size; id++) {
            write(id, id);
        }
    }

    int find(int id) throws IOException {
        requireValidId(id);
        int root = id;
        int traversed = 0;
        while (true) {
            int parent = read(root);
            requireValidParent(parent);
            if (parent == root) {
                break;
            }
            root = parent;
            if (++traversed >= size) {
                throw new ScanResultContractException(
                        "semantic component parent chain contains a cycle");
            }
        }

        int cursor = id;
        while (cursor != root) {
            int parent = read(cursor);
            requireValidParent(parent);
            write(cursor, root);
            cursor = parent;
        }
        return root;
    }

    void union(int left, int right) throws IOException {
        int leftRoot = find(left);
        int rightRoot = find(right);
        if (leftRoot == rightRoot) {
            return;
        }
        if (leftRoot < rightRoot) {
            write(rightRoot, leftRoot);
        } else {
            write(leftRoot, rightRoot);
        }
    }

    private int read(int id) throws IOException {
        parents.seek((long) id * WIDTH);
        return parents.readInt();
    }

    private void write(int id, int parent) throws IOException {
        parents.seek((long) id * WIDTH);
        parents.writeInt(parent);
    }

    private void requireValidId(int id) {
        if (id < 0 || id >= size) {
            throw new ScanResultContractException(
                    "semantic component id is outside the parent array");
        }
    }

    private void requireValidParent(int parent) {
        if (parent < 0 || parent >= size) {
            throw new ScanResultContractException(
                    "semantic component parent is outside the parent array");
        }
    }

    @Override
    public void close() throws IOException {
        parents.close();
    }
}
