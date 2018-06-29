package com.eveow.wtools.util.hash;

import java.util.Map;
import java.util.TreeMap;
import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * 一致性hash
 * 
 * @author wangjianping
 */
public class ConsistentHash<T> {

    /**
     * 虚拟节点连接符
     */
    private static final String VN_CONN = "_vn_";

    /**
     * 默认虚拟节点数量
     */
    private static final int DEFAULT_VIRTUAL_NUM = 100;

    /**
     * 虚拟节点数
     */
    private int virtualNodeNum;

    /**
     * hash函数
     */
    private HashFunction hashFunction;

    /**
     * 节点和虚拟节点map，看做hash环
     */
    private TreeMap<Integer, T> nodeMap;

    /**
     * 构造函数
     * 
     * @param nodes
     */
    public ConsistentHash(T... nodes) {
        this(DEFAULT_VIRTUAL_NUM, nodes);
    }

    /**
     * 构造函数
     * 
     * @param virtual
     * @param nodes
     */
    public ConsistentHash(int virtual, T... nodes) {
        this.virtualNodeNum = virtual;
        this.hashFunction = Hashing.murmur3_32();

        // 构建节点
        nodeMap = new TreeMap<>();
        if (nodes != null) {
            for (T node: nodes) {
                addNode(node);
            }
        }
    }

    /**
     * 获取对应的节点
     * 
     * @param v
     * @return
     */
    public T getNode(Object v) {
        Map.Entry<Integer, T> nodeEntry = nodeMap.higherEntry(hash(v.toString()));
        if (nodeEntry == null) {
            nodeEntry = nodeMap.firstEntry();
        }
        if (nodeEntry != null) {
            return nodeEntry.getValue();
        }
        return null;
    }

    /**
     * 动态添加节点
     * 
     * @param node
     */
    public void addNode(T node) {
        nodeMap.put(hash(node.toString()), node);
        // 添加虚拟节点
        for (int k = 1; k <= virtualNodeNum; k++) {
            String vn = node.toString() + VN_CONN + k;
            nodeMap.put(hash(vn), node);
        }
    }

    /**
     * 动态移除节点
     * 
     * @param node
     */
    public void removeNode(T node) {
        nodeMap.remove(hash(node.toString()));
        // 添加虚拟节点
        for (int k = 1; k <= virtualNodeNum; k++) {
            String vn = node + VN_CONN + k;
            nodeMap.remove(hash(vn));
        }
    }

    /**
     * hash函数计算值
     * 
     * @param name
     * @return
     */
    private int hash(String name) {
        return Math.abs(hashFunction.hashString(name, Charsets.UTF_8).asInt());
    }
}
