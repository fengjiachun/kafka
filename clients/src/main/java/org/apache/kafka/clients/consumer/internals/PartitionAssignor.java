/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.kafka.clients.consumer.internals;

import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.TopicPartition;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This interface is used to define custom partition assignment for use in
 * {@link org.apache.kafka.clients.consumer.KafkaConsumer}. Members of the consumer group subscribe
 * to the topics they are interested in and forward their subscriptions to a Kafka broker serving
 * as the group coordinator. The coordinator selects one member to perform the group assignment and
 * propagates the subscriptions of all members to it. Then {@link #assign(Cluster, Map)} is called
 * to perform the assignment and the results are forwarded back to each respective members
 *
 * In some cases, it is useful to forward additional metadata to the assignor in order to make
 * assignment decisions. For this, you can override {@link #subscription(Set)} and provide custom
 * userData in the returned Subscription. For example, to have a rack-aware assignor, an implementation
 * can use this user data to forward the rackId belonging to each member.
 *
 * 1. 消费者发送订阅信息给协调者
 * 2. 协调者收集所有消费者已经它们的订阅信息
 * 3. 选择第一个进来的消费者作为主消费者, 将所有消费者成员列表以及其订阅信息发送给主消费者
 * 4. 主消费者执行具体的分区分配算法
 * 5. 主消费者将分配结果同步回协调者
 * 6. 协调者收到分配结果, 将分区返回给每个消费者
 */
// 分区分配器接口, 负责执行分区分配的具体算法
public interface PartitionAssignor {

    /**
     * Return a serializable object representing the local member's subscription. This can include
     * additional information as well (e.g. local host/rack information) which can be leveraged in
     * {@link #assign(Cluster, Map)}.
     * @param topics Topics subscribed to through {@link org.apache.kafka.clients.consumer.KafkaConsumer#subscribe(java.util.Collection)}
     *               and variants
     * @return Non-null subscription with optional user data
     */
    // 每个消费者都有订阅的主题列表, Subscription是消费者的订阅信息
    Subscription subscription(Set<String> topics);

    /**
     * Perform the group assignment given the member subscriptions and current cluster metadata.
     * @param metadata Current topic/broker metadata known by consumer
     * @param subscriptions Subscriptions from all members provided through {@link #subscription(Set)}
     * @return A map from the members to their respective assignment. This should have one entry
     *         for all members who in the input subscription map.
     */
    // 只有主消费者会调用 assign(), 其中 subscriptions 是所有消费者的调用信息
    Map<String, Assignment> assign(Cluster metadata, Map<String, Subscription> subscriptions);


    /**
     * Callback which is invoked when a group member receives its assignment from the leader.
     * @param assignment The local member's assignment as provided by the leader in {@link #assign(Cluster, Map)}
     */
    // 分配到结果后的回调处理
    void onAssignment(Assignment assignment);


    /**
     * Unique name for this assignor (e.g. "range" or "roundrobin")
     * @return non-null unique name
     */
    String name();

    // 消费者的订阅信息, 即订阅了哪些主题
    class Subscription {
        private final List<String> topics;
        private final ByteBuffer userData;

        public Subscription(List<String> topics, ByteBuffer userData) {
            this.topics = topics;
            this.userData = userData;
        }

        public Subscription(List<String> topics) {
            this(topics, ByteBuffer.wrap(new byte[0]));
        }

        public List<String> topics() {
            return topics;
        }

        public ByteBuffer userData() {
            return userData;
        }

        @Override
        public String toString() {
            return "Subscription(" +
                    "topics=" + topics +
                    ')';
        }
    }

    // 消费者的分配结果, 即分配了哪些分区
    class Assignment {
        private final List<TopicPartition> partitions;
        private final ByteBuffer userData;

        public Assignment(List<TopicPartition> partitions, ByteBuffer userData) {
            this.partitions = partitions;
            this.userData = userData;
        }

        public Assignment(List<TopicPartition> partitions) {
            this(partitions, ByteBuffer.wrap(new byte[0]));
        }

        public List<TopicPartition> partitions() {
            return partitions;
        }

        public ByteBuffer userData() {
            return userData;
        }

        @Override
        public String toString() {
            return "Assignment(" +
                    "partitions=" + partitions +
                    ')';
        }
    }

}
