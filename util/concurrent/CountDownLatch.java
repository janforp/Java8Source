package java.util.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A synchronization aid that allows one or more threads to wait until a set of operations being performed in other threads completes.
 * -- 一种同步工具，它允许一个或多个线程等待，直到在其他线程中执行的一组操作完成为止。
 *
 * <p>A {@code CountDownLatch} is initialized with a given <em>count</em>.
 * -- {@code CountDownLatch}用给定的<em> count初始化
 *
 * The await methods block until the current count reaches zero due to invocations of the countDown method,
 * after which all waiting threads are released and any subsequent invocations of await return immediately.
 * -- 由于countCount方法的调用，直到当前计数达到零为止，await方法将阻塞，此后所有释放的线程将被释放，并且任何随后的await调用将立即返回。
 *
 * This is a one-shot phenomenon
 * -- the count cannot be reset.
 * -- 意思就是无法重复使用，因为count无法重置
 *
 * If you need a version that resets the count, consider using a {@link CyclicBarrier}.
 *
 * A CountDownLatch is a versatile synchronization tool and can be used for a number of purposes.
 * -- CountDownLatch是一种多功能的同步工具，可以用于多种用途。
 *
 *
 * A CountDownLatch initialized with a count of one serves as a simple on/off latch, or gate:
 * -- 用一个计数初始化的CountDownLatch用作简单的开/关锁存器或门：
 *
 * all threads invoking await wait at the gate until it is opened by a thread invoking countDown.
 * -- 所有调用线程的等待都在闸门处等待，直到它被调用countDown的线程打开。
 *
 * A CountDownLatch initialized to N can be used to make one thread wait until N threads have completed some action,
 * or some action has been completed N times.
 * -- 初始化为N的CountDownLatch可以用于使一个线程等待，直到N个线程完成某个动作或某个动作已经完成N次。!!!!！！！！！！！！！！
 *
 * A useful property of a CountDownLatch is that it doesn't require that threads calling countDown
 * wait for the count to reach zero before proceeding, it simply prevents any
 * thread from proceeding past an await until all threads could pass.
 * -- CountDownLatch的一个有用属性是，它不需要调用countDown的线程在继续进行操作之前就不必等待计数达到零，它只是防止任何线程经过等待状态直到所有线程都可以通过。
 *
 * <p><b>Sample usage:</b> Here is a pair of classes in which a group
 * of worker threads use two countdown latches:
 * <ul>
 * <li>The first is a start signal that prevents any worker from proceeding
 * until the driver is ready for them to proceed;
 * <li>The second is a completion signal that allows the driver to wait
 * until all workers have completed.
 * </ul>
 *
 *  <pre> {@code
 * class Driver { // ...
 *   void main() throws InterruptedException {
 *     CountDownLatch startSignal = new CountDownLatch(1);
 *     CountDownLatch doneSignal = new CountDownLatch(N);
 *
 *     for (int i = 0; i < N; ++i) // create and start threads
 *       new Thread(new Worker(startSignal, doneSignal)).start();
 *
 *     doSomethingElse();            // don't let run yet
 *     startSignal.countDown();      // let all threads proceed
 *     doSomethingElse();
 *     doneSignal.await();           // wait for all to finish
 *   }
 * }
 *
 * class Worker implements Runnable {
 *   private final CountDownLatch startSignal;
 *   private final CountDownLatch doneSignal;
 *   Worker(CountDownLatch startSignal, CountDownLatch doneSignal) {
 *     this.startSignal = startSignal;
 *     this.doneSignal = doneSignal;
 *   }
 *   public void run() {
 *     try {
 *       startSignal.await();
 *       doWork();
 *       doneSignal.countDown();
 *     } catch (InterruptedException ex) {} // return;
 *   }
 *
 *   void doWork() { ... }
 * }}</pre>
 *
 * <p>Another typical usage would be to divide a problem into N parts,
 * describe each part with a Runnable that executes that portion and
 * counts down on the latch, and queue all the Runnables to an
 * Executor.  When all sub-parts are complete, the coordinating thread
 * will be able to pass through await. (When threads must repeatedly
 * count down in this way, instead use a {@link CyclicBarrier}.)
 *
 *  <pre> {@code
 * class Driver2 { // ...
 *   void main() throws InterruptedException {
 *     CountDownLatch doneSignal = new CountDownLatch(N);
 *     Executor e = ...
 *
 *     for (int i = 0; i < N; ++i) // create and start threads
 *       e.execute(new WorkerRunnable(doneSignal, i));
 *
 *     doneSignal.await();           // wait for all to finish
 *   }
 * }
 *
 * class WorkerRunnable implements Runnable {
 *   private final CountDownLatch doneSignal;
 *   private final int i;
 *   WorkerRunnable(CountDownLatch doneSignal, int i) {
 *     this.doneSignal = doneSignal;
 *     this.i = i;
 *   }
 *   public void run() {
 *     try {
 *       doWork(i);
 *       doneSignal.countDown();
 *     } catch (InterruptedException ex) {} // return;
 *   }
 *
 *   void doWork() { ... }
 * }}</pre>
 *
 * <p>Memory consistency effects: Until the count reaches
 * zero, actions in a thread prior to calling
 * {@code countDown()}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions following a successful return from a corresponding
 * {@code await()} in another thread.
 *
 * @author Doug Lea
 * @since 1.5
 */
@SuppressWarnings("all")
public class CountDownLatch {

    /**
     * Synchronization control For CountDownLatch.
     * Uses AQS state to represent count.
     */
    private static final class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            setState(count);
        }

        int getCount() {
            return getState();
        }

        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        /**
         * 更新 AQS.state 值，每调用一次，state值减一，当state -1 正好为0时，返回true
         */
        protected boolean tryReleaseShared(int releases) {
            // Decrement count; signal when transition to zero -- 递减计数；过渡到零时发出信号
            for (; ; ) {
                //获取当前AQS.state
                int c = getState();
                //条件成立：说明前面已经有线程 触发 唤醒操作了，这里返回false
                if (c == 0) {
                    return false;
                }

                //执行到这里，说明 state > 0

                int nextc = c - 1;

                //cas成功，说明当前线程执行 tryReleaseShared 方法 c-1之前，没有其它线程 修改过 state。
                if (compareAndSetState(c, nextc)) {
                    //nextc == 0 ：true ，说明当前调用 countDown() 方法的线程 就是需要触发 唤醒操作的线程.
                    return nextc == 0;
                }
            }
        }
    }

    private final Sync sync;

    /**
     * Constructs a {@code CountDownLatch} initialized with the given count.
     *
     * @param count the number of times {@link #countDown} must be invoked
     * before threads can pass through {@link #await}
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public CountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        this.sync = new Sync(count);
    }

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, unless the thread is {@linkplain Thread#interrupt interrupted}.
     *
     * <p>If the current count is zero then this method returns immediately.
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of two things happen:
     * <ul>
     * <li>The count reaches zero due to invocations of the
     * {@link #countDown} method; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     * while waiting
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, unless the thread is {@linkplain Thread#interrupt interrupted},
     * or the specified waiting time elapses.
     *
     * <p>If the current count is zero then this method returns immediately
     * with the value {@code true}.
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of three things happen:
     * <ul>
     * <li>The count reaches zero due to invocations of the
     * {@link #countDown} method; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     *
     * <p>If the count reaches zero then the method returns with the
     * value {@code true}.
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if the count reached zero and {@code false}
     * if the waiting time elapsed before the count reached zero
     * @throws InterruptedException if the current thread is interrupted
     * while waiting
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * Decrements the count of the latch, releasing all waiting threads if
     * the count reaches zero.
     *
     * <p>If the current count is greater than zero then it is decremented.
     * If the new count is zero then all waiting threads are re-enabled for
     * thread scheduling purposes.
     *
     * <p>If the current count equals zero then nothing happens.
     */
    public void countDown() {
        sync.releaseShared(1);
    }

    /**
     * Returns the current count.
     *
     * <p>This method is typically used for debugging and testing purposes.
     *
     * @return the current count
     */
    public long getCount() {
        return sync.getCount();
    }

    /**
     * Returns a string identifying this latch, as well as its state.
     * The state, in brackets, includes the String {@code "Count ="}
     * followed by the current count.
     *
     * @return a string identifying this latch, as well as its state
     */
    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
}