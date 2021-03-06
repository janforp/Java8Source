package java.util.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A synchronization aid that allows a set of threads to all wait for
 * each other to reach a common barrier point.  CyclicBarriers are
 * useful in programs involving a fixed sized party of threads that
 * must occasionally wait for each other. The barrier is called
 * <em>cyclic</em> because it can be re-used after the waiting threads
 * are released.
 *
 * <p>A {@code CyclicBarrier} supports an optional {@link Runnable} command
 * that is run once per barrier point, after the last thread in the party
 * arrives, but before any threads are released.
 * This <em>barrier action</em> is useful
 * for updating shared-state before any of the parties continue.
 *
 * <p><b>Sample usage:</b> Here is an example of using a barrier in a
 * parallel decomposition design:
 *
 * <pre> {@code
 * class Solver {
 *   final int N;
 *   final float[][] data;
 *   final CyclicBarrier barrier;
 *
 *   class Worker implements Runnable {
 *     int myRow;
 *     Worker(int row) { myRow = row; }
 *     public void run() {
 *       while (!done()) {
 *         processRow(myRow);
 *
 *         try {
 *           barrier.await();
 *         } catch (InterruptedException ex) {
 *           return;
 *         } catch (BrokenBarrierException ex) {
 *           return;
 *         }
 *       }
 *     }
 *   }
 *
 *   public Solver(float[][] matrix) {
 *     data = matrix;
 *     N = matrix.length;
 *     Runnable barrierAction =
 *       new Runnable() { public void run() { mergeRows(...); }};
 *     barrier = new CyclicBarrier(N, barrierAction);
 *
 *     List<Thread> threads = new ArrayList<Thread>(N);
 *     for (int i = 0; i < N; i++) {
 *       Thread thread = new Thread(new Worker(i));
 *       threads.add(thread);
 *       thread.start();
 *     }
 *
 *     // wait until done
 *     for (Thread thread : threads)
 *       thread.join();
 *   }
 * }}</pre>
 *
 * Here, each worker thread processes a row of the matrix then waits at the
 * barrier until all rows have been processed. When all rows are processed
 * the supplied {@link Runnable} barrier action is executed and merges the
 * rows. If the merger
 * determines that a solution has been found then {@code done()} will return
 * {@code true} and each worker will terminate.
 *
 * <p>If the barrier action does not rely on the parties being suspended when
 * it is executed, then any of the threads in the party could execute that
 * action when it is released. To facilitate this, each invocation of
 * {@link #await} returns the arrival index of that thread at the barrier.
 * You can then choose which thread should execute the barrier action, for
 * example:
 * <pre> {@code
 * if (barrier.await() == 0) {
 *   // log the completion of this iteration
 * }}</pre>
 *
 * <p>The {@code CyclicBarrier} uses an all-or-none breakage model
 * for failed synchronization attempts: If a thread leaves a barrier
 * point prematurely because of interruption, failure, or timeout, all
 * other threads waiting at that barrier point will also leave
 * abnormally via {@link BrokenBarrierException} (or
 * {@link InterruptedException} if they too were interrupted at about
 * the same time).
 *
 * <p>Memory consistency effects: Actions in a thread prior to calling
 * {@code await()}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions that are part of the barrier action, which in turn
 * <i>happen-before</i> actions following a successful return from the
 * corresponding {@code await()} in other threads.
 *
 * -- 循环屏障，它没有什么新东西，使用了Condition条件队列
 *
 * @author Doug Lea
 * @see CountDownLatch
 * @since 1.5
 */
@SuppressWarnings("all")
public class CyclicBarrier {

    /**
     * Each use of the barrier is represented as a generation instance.
     * The generation changes whenever the barrier is tripped, or
     * is reset. There can be many generations associated with threads
     * using the barrier - due to the non-deterministic way the lock
     * may be allocated to waiting threads - but only one of these
     * can be active at a time (the one to which {@code count} applies)
     * and all the rest are either broken or tripped.
     * There need not be an active generation if there has been a break
     * but no subsequent reset.
     * 表示 “代”这个概念
     */
    private static class Generation {

        //表示当前“代”是否被打破，如果代被打破 ，那么再来到这一代的线程 就会直接抛出 BrokenException异常
        //且在这一代 挂起的线程 都会被唤醒，然后抛出 BrokerException异常。
        boolean broken = false;
    }

    /**
     * The lock for guarding barrier entry
     */
    //因为barrier实现是依赖于Condition条件队列的，condition条件队列必须依赖lock才能使用。
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Condition to wait on until tripped
     */
    //线程挂起实现使用的 condition 队列。条件：当前代所有线程到位，这个条件队列内的所有线程 才会被唤醒。
    private final Condition trip = lock.newCondition();

    /**
     * The number of parties
     */
    //Barrier需要参与进来的线程数量
    private final int parties;

    /* The command to run when tripped */
    //当前代 最后一个到位的线程 需要执行的事件
    private final Runnable barrierCommand;

    /**
     * The current generation
     */
    //表示barrier对象 当前 “代”
    private Generation generation = new Generation();

    /**
     * Number of parties still waiting. Counts down from parties to 0
     * on each generation.  It is reset to parties on each new
     * generation or when broken.
     */
    //表示当前“代”还有多少个线程 未到位。
    //初始值为parties
    private int count;

    /**
     * Updates state on barrier trip and wakes up everyone.
     * Called only while holding lock.
     *
     * 开启下一代，当这一代 所有线程到位后（假设barrierCommand不为空，还需要最后一个线程执行完事件），
     * 会调用nextGeneration()开启新的一代。
     *
     * 开启新的一代
     * 1.唤醒trip条件队列内挂起的线程，被唤醒的线程 会依次 获取到lock，然后依次退出await方法。
     * 2.重置count 为 parties
     * 3.创建一个新的generation对象，表示新的一代
     */
    private void nextGeneration() {
        //将在trip条件队列内挂起的线程 全部唤醒
        //signal completion of last generation
        trip.signalAll();

        //重置count为parties
        // set up next generation
        count = parties;

        //开启新的一代..使用一个新的 generation对象，表示新的一代，新的一代和上一代 没有任何关系。
        generation = new Generation();
    }

    /**
     * Sets current barrier generation as broken and wakes up everyone.
     * Called only while holding lock.
     * 打破barrier屏障，在屏障内的线程 都会抛出异常..
     */
    private void breakBarrier() {
        //将代中的broken设置为true，表示这一代是被打破了的，再来到这一代的线程，直接抛出异常.
        generation.broken = true;
        //重置count为parties
        count = parties;
        //将在trip条件队列内挂起的线程 全部唤醒，唤醒后的线程 会检查当前代 是否是打破的，
        //如果是打破的话，接下来的逻辑和 开启下一代 唤醒的逻辑不一样.
        trip.signalAll();
    }

    /**
     * 这是一个很重的方法
     * Main barrier code, covering the various policies.-- 主要障碍代码，涵盖了各种政策。
     *
     * timed：表示当前调用await方法的线程是否指定了 超时时长，如果true 表示 线程是响应超时的
     * nanos：线程等待超时时长 纳秒，如果timed == false ,nanos == 0
     *
     * @return 返回 index：
     */
    private int dowait(boolean timed, long nanos) throws InterruptedException, BrokenBarrierException, TimeoutException {
        //获取barrier全局锁对象
        final ReentrantLock lock = this.lock;
        /**
         * 为什么要加锁呢？
         * 因为 barrier的挂起 和 唤醒 依赖的组件是 condition。而 condition 必须属于某个锁
         */
        lock.lock();
        try {
            //获取barrier当前的 “代”
            final Generation g = generation;

            //如果当前代是已经被打破状态，则当前调用await方法的线程，直接抛出Broken异常
            if (g.broken) {
                throw new BrokenBarrierException();
            }

            //如果当前线程的中断标记位 为 true，则打破当前代，然后当前线程抛出 中断异常
            if (Thread.interrupted()) {
                //1.设置当前代的状态为broken状态  2.唤醒在trip 条件队列内的线程
                breakBarrier();
                throw new InterruptedException();
            }

            /**
             * 执行到这里，说明
             * 1.当前线程中断状态是正常的 false， 没有中断
             * 2.当前代的broken为 false（未打破状态），没有打破
             *
             * 大部分情况会执行到这里的
             */
            //假设 parties 初始是 5，那么index对应的值为 4,3,2,1,0
            //也就是每次正常的调用该方法，count都会-1，说明一个线程满足了条件
            int index = --count;
            if (index == 0) {  // tripped -- 绊倒了
                //条件成立：说明当前线程是最后一个到达barrier的线程，此时需要做什么呢？
                /**
                 * ranAction=true表示 最后一个线程 执行 command 时未抛异常。
                 * ranAction=false，表示最后一个线程执行 command 时抛出异常了.
                 * command 就是创建 barrier对象时 指定的第二个 Runnable接口实现，这个可以为null
                 */
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    if (command != null) {
                        //条件成立：说明创建barrier对象时 指定 Runnable接口了，
                        //这个时候最后一个到达的线程 就需要执行这个接口
                        command.run();//这个方法可能抛出异常
                        //如果抛出了异常，则不会往下执行了，只会执行finally代码块!!!
                    }
                    //command.run()未抛出异常的话，那么线程会执行到这里。
                    ranAction = true;

                    /**
                     * 开启新的一代
                     * 1.唤醒trip条件队列内挂起的线程，被唤醒的线程 会依次 获取到lock，然后依次退出await方法。
                     * 2.重置count 为 parties
                     * 3.创建一个新的generation对象，表示新的一代
                     */
                    nextGeneration();
                    //返回0，因为当前线程是此 代 最后一个到达的线程，所以Index == 0
                    return 0;
                } finally {
                    if (!ranAction) {
                        //如果command.run()执行抛出异常的话，会进入到这里。
                        breakBarrier();
                    }
                }
            }

            /**
             * 执行到这里，说明当前线程 并不是最后一个到达Barrier的线程..此时需要进入一个自旋中.
             * loop until tripped(条件满足), broken(当前代被打破), interrupted(线程被中断), or timed out(等待超时)
             */
            for (; ; ) {
                try {
                    if (!timed) {
                        //条件成立：timed 为 false,说明当前线程是不指定超时时间的
                        //当前线程 会 释放掉lock，然后进入到trip条件队列的尾部，然后挂起自己，等待被唤醒。
                        trip.await();//阻塞！！
                    } else if (nanos > 0L) {
                        //timed 为 true并且时间>0,说明当前线程调用await方法时 是指定了 超时时间的！
                        nanos = trip.awaitNanos(nanos);//阻塞！！
                    }
                } catch (InterruptedException ie) {
                    //抛出中断异常，会进来这里。
                    //什么时候会抛出InterruptedException异常呢？
                    //Node节点在 条件队列内 时 收到中断信号时 会抛出中断异常！

                    //条件一：g == generation 成立，说明当前代并没有变化。
                    if (g == generation
                            //条件二：! g.broken 当前代如果没有被打破，那么当前线程就去打破，并且抛出异常..
                            && !g.broken) {

                        //当前代如果没有被打破，那么当前线程就去打破，并且抛出异常..
                        breakBarrier();
                        throw ie;
                    } else {

                        /**
                         * 执行到else有几种情况？
                         * 1.代发生了变化(g != generation)，这个时候就不需要抛出中断异常了，
                         *   因为 代已经更新了，这里唤醒后就走正常逻辑了..只不过设置下 中断标记。
                         * 2.代没有发生变化，但是代被打破了(g == generation但是g.broken==true)，
                         *   此时也不用返回中断异常，执行到下面的时候会抛出  brokenBarrier异常。
                         *   也记录下中断标记位。
                         *
                         *  We're about to finish waiting even if we had not
                         *  been interrupted, so this interrupt is deemed to
                         *  "belong" to subsequent execution.
                         *  -- 即使我们没有被中断，我们也将完成等待，因此该中断被视为“属于”后续执行。
                         */
                        Thread.currentThread().interrupt();
                    }
                }

                //执行到这里，那么肯定是唤醒了

                /**
                 * 唤醒后，执行到这里，有几种情况？
                 * 1.正常情况，当前barrier开启了新的一代（trip.signalAll()）
                 * 2.当前Generation被打破，此时也会唤醒所有在trip上挂起的线程
                 * 3.当前线程trip(条件队列)中等待超时，然后主动转移到 阻塞队列 然后获取到锁 唤醒。
                 */
                if (g.broken) {
                    //条件成立：当前代已经被打破
                    //处理情况2.当前Generation被打破，此时也会唤醒所有在trip上挂起的线程
                    throw new BrokenBarrierException();
                }

                /**
                 * 唤醒后，执行到这里，有几种情况？
                 * 1.正常情况，当前barrier开启了新的一代（trip.signalAll()）
                 * 3.当前线程trip中等待超时，然后主动转移到 阻塞队列 然后获取到锁 唤醒。
                 */
                if (g != generation) {
                    /**
                     * 处理情况1.正常情况，当前barrier开启了新的一代（trip.signalAll()）
                     * 条件成立：说明当前线程挂起期间，最后一个线程到位了，
                     * 然后触发了开启新的一代的逻辑，此时唤醒trip条件队列内的线程。
                     * 返回当前线程的index。
                     */
                    return index;
                }

                //唤醒后，执行到这里，有几种情况？
                //3.当前线程trip中等待超时，然后主动转移到 阻塞队列 然后获取到锁 唤醒。

                /**
                 * @see Condition#awaitNanos(long)
                 */
                if (timed && nanos <= 0L) {
                    //处理情况3.当前线程trip中等待超时，然后主动转移到 阻塞队列 然后获取到锁 唤醒。
                    //打破barrier
                    breakBarrier();
                    //抛出超时异常.
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and which
     * will execute the given barrier action when the barrier is tripped,
     * performed by the last thread entering the barrier.
     *
     * @param parties the number of threads that must invoke {@link #await}
     * before the barrier is tripped
     * @param barrierAction the command to execute when the barrier is
     * tripped, or {@code null} if there is no action
     * @throws IllegalArgumentException if {@code parties} is less than 1
     * parties：Barrier需要参与的线程数量，每次屏障需要参与的线程数
     * barrierAction：当前“代”最后一个到位的线程，需要执行的事件（可以为null）
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        //因为小于等于0 的barrier没有任何意义..
        if (parties <= 0) {
            throw new IllegalArgumentException();
        }

        this.parties = parties;
        //count的初始值 就是parties，后面当前代每到位一个线程，count--
        this.count = parties;
        this.barrierCommand = barrierAction;
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and
     * does not perform a predefined action when the barrier is tripped.
     *
     * @param parties the number of threads that must invoke {@link #await}
     * before the barrier is tripped
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    /**
     * Returns the number of parties required to trip this barrier.
     *
     * @return the number of parties required to trip this barrier
     */
    public int getParties() {
        return parties;
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * {@code await} on this barrier.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * {@code await} is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while waiting,
     * then all other waiting threads will throw
     * {@link BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @return the arrival index of the current thread, where index
     * {@code getParties() - 1} indicates the first
     * to arrive and zero indicates the last to arrive
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     * @throws BrokenBarrierException if <em>another</em> thread was
     * interrupted or timed out while the current thread was
     * waiting, or the barrier was reset, or the barrier was
     * broken when {@code await} was called, or the barrier
     * action (if present) failed due to an exception
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * {@code await} on this barrier, or the specified waiting time elapses.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>The specified timeout elapses; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then {@link TimeoutException}
     * is thrown. If the time is less than or equal to zero, the
     * method will not wait at all.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * {@code await} is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while
     * waiting, then all other waiting threads will throw {@link
     * BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @param timeout the time to wait for the barrier
     * @param unit the time unit of the timeout parameter
     * @return the arrival index of the current thread, where index
     * {@code getParties() - 1} indicates the first
     * to arrive and zero indicates the last to arrive
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     * @throws TimeoutException if the specified timeout elapses.
     * In this case the barrier will be broken.
     * @throws BrokenBarrierException if <em>another</em> thread was
     * interrupted or timed out while the current thread was
     * waiting, or the barrier was reset, or the barrier was broken
     * when {@code await} was called, or the barrier action (if
     * present) failed due to an exception
     */
    public int await(long timeout, TimeUnit unit)
            throws InterruptedException,
            BrokenBarrierException,
            TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     * Queries if this barrier is in a broken state.
     *
     * @return {@code true} if one or more parties broke out of this
     * barrier due to interruption or timeout since
     * construction or the last reset, or a barrier action
     * failed due to an exception; {@code false} otherwise.
     */
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the barrier to its initial state.  If any parties are
     * currently waiting at the barrier, they will return with a
     * {@link BrokenBarrierException}. Note that resets <em>after</em>
     * a breakage has occurred for other reasons can be complicated to
     * carry out; threads need to re-synchronize in some other way,
     * and choose one to perform the reset.  It may be preferable to
     * instead create a new barrier for subsequent use.
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of parties currently waiting at the barrier.
     * This method is primarily useful for debugging and assertions.
     *
     * @return the number of parties currently blocked in {@link #await}
     */
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}