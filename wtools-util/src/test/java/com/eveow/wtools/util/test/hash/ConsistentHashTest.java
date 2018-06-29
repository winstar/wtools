package com.eveow.wtools.util.test.hash;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

import com.eveow.wtools.util.hash.ConsistentHash;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author wangjianping
 */
public class ConsistentHashTest {

    @Test
    public void testGuavaConsistentHash1() {
        long now = System.currentTimeMillis();

        int[] num = new int[10];
        for (int i = 0; i < 1000000; i++) {
            int x = Hashing.murmur3_32().hashString("testhh" + i, Charsets.UTF_8).asInt();
            int k = Hashing.consistentHash(x, 10);
            num[k] = num[k] + 1;
        }

        for (int i = 0; i < 10; i++) {
            System.out.println(num[i]);
        }

        System.out.println(System.currentTimeMillis() - now);
    }

    @Test
    public void testGuavaConsistentHash2() {
        int nodeNum = 999;

        int same = 0;
        int change = 0;

        for (int i = 0; i < 100000; i++) {
            long x = Hashing.murmur3_32().hashString("test" + i, Charsets.UTF_8).asInt();
            if (Hashing.consistentHash(x, nodeNum) == Hashing.consistentHash(x, nodeNum + 1)) {
                same++;
            } else {
                change++;
            }
        }
        System.out.println(same);
        System.out.println(change);
        System.out.println(1.0 * change / (same + change));
    }

    @Test
    public void testMyHash1() {

        String[] nodes = new String[] { "node1", "node2", "node3", "node4" };
        ConsistentHash<String> consistentHash = new ConsistentHash<>(nodes);

        Map<String, Integer> nums = Arrays.stream(nodes).collect(Collectors.toMap(s -> s, s -> 0));

        for (int i = 0; i < 1000000; i++) {
            String n = consistentHash.getNode("test_node" + i);
            nums.put(n, nums.get(n) + 1);

//            if (i == 300000) {
//                consistentHash.addNode("node5");
//                nums.put("node5", 0);
//            }
//            if (i == 600000) {
//                consistentHash.removeNode("node1");
//            }
        }

        nums.forEach((k, v) -> System.out.println(k + ": " + v));
    }

    @Test
    public void testMyHash2() {

        String[] nodes1 = new String[] { "node1", "node2", "node3", "node4" };
        ConsistentHash<String> consistentHash1 = new ConsistentHash<>(nodes1);

        String[] nodes2 = new String[] { "node1", "node2", "node3", "node4", "node5" };
        ConsistentHash<String> consistentHash2 = new ConsistentHash<>(nodes2);

        int same = 0;
        int change = 0;

        for (int i = 0; i < 1000000; i++) {
            String v = "test_v_" + i;
            if (Objects.equals(consistentHash1.getNode(v), consistentHash2.getNode(v))) {
                same++;
            } else {
                change++;
            }
        }
        System.out.println(same);
        System.out.println(change);
        System.out.println(1.0 * change / (same + change));
    }
}
