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

    DiskUnionFind(Path path, int size) throws IOException {
        parents = new RandomAccessFile(path.toFile(), "rw");
        parents.setLength((long) size * WIDTH);
        for (int id = 0; id < size; id++) {
            write(id, id);
        }
    }

    int find(int id) throws IOException {
        int parent = read(id);
        if (parent == id) {
            return id;
        }
        int root = find(parent);
        if (root != parent) {
            write(id, root);
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

    @Override
    public void close() throws IOException {
        parents.close();
    }
}
