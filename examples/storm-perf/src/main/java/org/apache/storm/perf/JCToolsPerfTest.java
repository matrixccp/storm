/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.apache.storm.perf;

import java.util.concurrent.locks.LockSupport;

import org.apache.storm.utils.MutableLong;
import org.jctools.queues.MpscArrayQueue;

public class JCToolsPerfTest {
    public static void main(String[] args) throws Exception {
//        oneProducer1Consumer();
//        twoProducer1Consumer();
//        threeProducer1Consumer();
//        oneProducer2Consumers();
//        producerFwdConsumer();

//        JCQueue spoutQ = new JCQueue("spoutQ", 1024, 100, 0);
//        JCQueue ackQ = new JCQueue("ackQ", 1024, 100, 0);
//
//        final AckingProducer ackingProducer = new AckingProducer(spoutQ, ackQ);
//        final Acker acker = new Acker(ackQ, spoutQ);
//
//        runAllThds(ackingProducer, acker);

        while (true) {
            Thread.sleep(1000);
        }

    }

    private static void oneProducer1Consumer() {
        MpscArrayQueue<Object> q1 = new MpscArrayQueue<Object>(50_000);

        final Prod prod1 = new Prod(q1);
        final Cons cons1 = new Cons(q1);

        runAllThds(prod1, cons1);
    }

    private static void twoProducer1Consumer() {
        MpscArrayQueue<Object> q1 = new MpscArrayQueue<Object>(50_000);

        final Prod prod1 = new Prod(q1);
        final Prod prod2 = new Prod(q1);
        final Cons cons1 = new Cons(q1);

        runAllThds(prod1, cons1, prod2);
    }

    private static void threeProducer1Consumer() {
        MpscArrayQueue<Object> q1 = new MpscArrayQueue<Object>(50_000);

        final Prod prod1 = new Prod(q1);
        final Prod prod2 = new Prod(q1);
        final Prod prod3 = new Prod(q1);
        final Cons cons1 = new Cons(q1);

        runAllThds(prod1, prod2, prod3, cons1);
    }


    private static void oneProducer2Consumers() {
        MpscArrayQueue<Object> q1 = new MpscArrayQueue<Object>(50_000);
        MpscArrayQueue<Object> q2 = new MpscArrayQueue<Object>(50_000);

        final Prod2 prod1 = new Prod2(q1, q2);
        final Cons cons1 = new Cons(q1);
        final Cons cons2 = new Cons(q2);

        runAllThds(prod1, cons1, cons2);
    }

    public static void runAllThds(MyThd... threads) {
        for (Thread thread : threads) {
            thread.start();
        }
        addShutdownHooks(threads);
    }

    public static void addShutdownHooks(MyThd... threads) {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.err.println("Stopping");
                for (MyThd thread : threads) {
                    thread.halt = true;
                }

                for (Thread thread : threads) {
                    System.err.println("Waiting for " + thread.getName());
                    thread.join();
                }

                for (MyThd thread : threads) {
                    System.err.printf("%s : %d,  Throughput: %,d \n", thread.getName(), thread.count, thread.throughput());
                }
            } catch (InterruptedException e) {
                return;
            }
        }));

    }

}


abstract class MyThd extends Thread {
    public long count = 0;
    public long runTime = 0;
    public boolean halt = false;

    public MyThd(String thdName) {
        super(thdName);
    }

    public long throughput() {
        return getCount() / (runTime / 1000);
    }

    public long getCount() {
        return count;
    }
}

class Prod extends MyThd {
    private final MpscArrayQueue<Object> q;

    public Prod(MpscArrayQueue<Object> q) {
        super("Producer");
        this.q = q;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();

        while (!halt) {
            ++count;
            while (!q.offer(count)) {
                if (Thread.interrupted()) {
                    return;
                }
            }
        }
        runTime = System.currentTimeMillis() - start;
    }

}

// writes to two queues
class Prod2 extends MyThd {
    private final MpscArrayQueue<Object> q1;
    private final MpscArrayQueue<Object> q2;

    public Prod2(MpscArrayQueue<Object> q1, MpscArrayQueue<Object> q2) {
        super("Producer2");
        this.q1 = q1;
        this.q2 = q2;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();

        while (!halt) {
            q1.offer(++count);
            q2.offer(count);
        }
        runTime = System.currentTimeMillis() - start;
    }
}


class Cons extends MyThd {
    public final MutableLong counter = new MutableLong(0);
    private final MpscArrayQueue<Object> q;

    public Cons(MpscArrayQueue<Object> q) {
        super("Consumer");
        this.q = q;
    }

    @Override
    public void run() {
        Handler handler = new Handler();
        long start = System.currentTimeMillis();

        while (!halt) {
            int x = q.drain(handler);
            if (x == 0) {
                LockSupport.parkNanos(1);
            } else {
                counter.increment();
            }
        }
        runTime = System.currentTimeMillis() - start;
    }

    @Override
    public long getCount() {
        return counter.get();
    }

    private class Handler implements org.jctools.queues.MessagePassingQueue.Consumer<Object> {
        @Override
        public void accept(Object event) {
            counter.increment();
        }
    }
}
