package java.util.concurrent.locks;

import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Provides a framework for implementing blocking locks and related synchronizers (semaphores, events, etc) that rely on first-in-first-out (FIFO) wait queues.
 * -- 提供一个框架，用于实现依赖于先进先出（FIFO）等待队列的阻塞锁和相关的同步器（信号灯，事件等）。
 *
 *
 * This class is designed to be a useful basis for most kinds of synchronizers that rely on a single atomic {@code int} value to represent state.
 * -- 此类旨在为大多数依赖单个原子{@code int}值表示状态的同步器提供有用的基础。
 *
 * Subclasses must define the protected methods that change this state, and which define what that state means in terms of this object being acquired or released.
 * -- 子类必须定义更改此状态的受保护方法，并定义该状态对于获取或释放此对象而言意味着什么。
 *
 * Given these, the other methods in this class carry out all queuing and blocking mechanics.
 * -- 鉴于这些，此类中的其他方法将执行所有排队和阻塞机制。
 *
 * Subclasses can maintain
 * other state fields, but only the atomically updated {@code int}
 * value manipulated using methods {@link #getState}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 *
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * {@code AbstractQueuedSynchronizer} does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 *
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 *
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * {@code AbstractQueuedSynchronizer} method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 *
 * <p>This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an {@code AbstractQueuedSynchronizer} for their
 * synchronization mechanics.
 *
 * <p>Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a {@code readObject} method that restores this to a known
 * initial state upon deserialization.
 *
 * <h3>Usage</h3>
 *
 * <p>To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 *
 * <ul>
 * <li> {@link #tryAcquire}
 * <li> {@link #tryRelease}
 * <li> {@link #tryAcquireShared}
 * <li> {@link #tryReleaseShared}
 * <li> {@link #isHeldExclusively}
 * </ul>
 *
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * {@code final} because they cannot be independently varied.
 *
 * <p>You may also find the inherited methods from {@link
 * AbstractOwnableSynchronizer} useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 *
 * <p>Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 *
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 *
 * (Shared mode is similar but may involve cascading signals.)
 *
 * <p id="barging">Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may <em>barge</em> ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define {@code tryAcquire} and/or {@code tryAcquireShared} to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a <em>fair</em> FIFO acquisition order.
 * In particular, most fair synchronizers can define {@code tryAcquire}
 * to return {@code false} if {@link #hasQueuedPredecessors} (a method
 * specifically designed to be used by fair synchronizers) returns
 * {@code true}.  Other variations are possible.
 *
 * <p>Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of {@code tryAcquire} interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 *
 * <p>This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on {@code int} state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 *
 * <h3>Usage Examples</h3>
 *
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes
 * one of the instrumentation methods:
 *
 *  <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Reports whether in locked state
 *     protected boolean isHeldExclusively() {
 *       return getState() == 1;
 *     }
 *
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (getState() == 0) throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Provides a Condition
 *     Condition newCondition() { return new ConditionObject(); }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()                { sync.acquire(1); }
 *   public boolean tryLock()          { return sync.tryAcquire(1); }
 *   public void unlock()              { sync.release(1); }
 *   public Condition newCondition()   { return sync.newCondition(); }
 *   public boolean isLocked()         { return sync.isHeldExclusively(); }
 *   public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 *
 * <p>Here is a latch class that is like a
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * except that it only requires a single {@code signal} to
 * fire. Because a latch is non-exclusive, it uses the {@code shared}
 * acquire and release methods.
 *
 *  <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
@SuppressWarnings("all")
public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() {
    }

    /**
     * Wait queue node class.
     *
     * <p>The wait queue is a variant of a "CLH" (Craig, Landin, and
     * Hagersten) lock queue. CLH locks are normally used for
     * spinlocks.  We instead use them for blocking synchronizers, but
     * use the same basic tactic of holding some of the control
     * information about a thread in the predecessor of its node.  A
     * "status" field in each node keeps track of whether a thread
     * should block.  A node is signalled when its predecessor
     * releases.  Each node of the queue otherwise serves as a
     * specific-notification-style monitor holding a single waiting
     * thread. The status field does NOT control whether threads are
     * granted locks etc though.  A thread may try to acquire if it is
     * first in the queue. But being first does not guarantee success;
     * it only gives the right to contend.  So the currently released
     * contender thread may need to rewait.
     *
     * <p>To enqueue into a CLH lock, you atomically splice it in as new
     * tail. To dequeue, you just set the head field.
     * <pre>
     *      +------+  prev +-----+       +-----+
     * head |      | <---- |     | <---- |     |  tail
     *      +------+       +-----+       +-----+
     * </pre>
     *
     * <p>Insertion into a CLH queue requires only a single atomic
     * operation on "tail", so there is a simple atomic point of
     * demarcation from unqueued to queued. Similarly, dequeuing
     * involves only updating the "head". However, it takes a bit
     * more work for nodes to determine who their successors are,
     * in part to deal with possible cancellation due to timeouts
     * and interrupts.
     *
     * <p>The "prev" links (not used in original CLH locks), are mainly
     * needed to handle cancellation. If a node is cancelled, its
     * successor is (normally) relinked to a non-cancelled
     * predecessor. For explanation of similar mechanics in the case
     * of spin locks, see the papers by Scott and Scherer at
     * http://www.cs.rochester.edu/u/scott/synchronization/
     *
     * <p>We also use "next" links to implement blocking mechanics.
     * The thread id for each node is kept in its own node, so a
     * predecessor signals the next node to wake up by traversing
     * next link to determine which thread it is.  Determination of
     * successor must avoid races with newly queued nodes to set
     * the "next" fields of their predecessors.  This is solved
     * when necessary by checking backwards from the atomically
     * updated "tail" when a node's successor appears to be null.
     * (Or, said differently, the next-links are an optimization
     * so that we don't usually need a backward scan.)
     *
     * <p>Cancellation introduces some conservatism to the basic
     * algorithms.  Since we must poll for cancellation of other
     * nodes, we can miss noticing whether a cancelled node is
     * ahead or behind us. This is dealt with by always unparking
     * successors upon cancellation, allowing them to stabilize on
     * a new predecessor, unless we can identify an uncancelled
     * predecessor who will carry this responsibility.
     *
     * <p>CLH queues need a dummy header node to get started. But
     * we don't create them on construction, because it would be wasted
     * effort if there is never contention. Instead, the node
     * is constructed and head and tail pointers are set upon first
     * contention.
     *
     * <p>Threads waiting on Conditions use the same nodes, but
     * use an additional link. Conditions only need to link nodes
     * in simple (non-concurrent) linked queues because they are
     * only accessed when exclusively held.  Upon await, a node is
     * inserted into a condition queue.  Upon signal, the node is
     * transferred to the main queue.  A special value of status
     * field is used to mark which queue a node is on.
     *
     * <p>Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     * Scherer and Michael Scott, along with members of JSR-166
     * expert group, for helpful ideas, discussions, and critiques
     * on the design of this class.
     */
    static final class Node {

        /**
         * Marker to indicate a node is waiting in shared mode
         */
        //枚举：共享模式
        static final Node SHARED = new Node();

        /**
         * Marker to indicate a node is waiting in exclusive mode
         * /枚举：独占模式
         */
        static final Node EXCLUSIVE = null;

        /**
         * waitStatus value to indicate thread has cancelled
         * 表示当前节点处于 取消 状态
         */
        static final int CANCELLED = 1;

        /**
         * waitStatus value to indicate successor's thread needs unparking
         *
         * -- waitStatus值，指示后续线程需要被唤醒
         * 注释：表示当前节点需要唤醒他的后继节点。（SIGNAL 表示其实是 后继节点的状态，需要当前节点去喊它）
         */
        static final int SIGNAL = -1;

        /**
         * waitStatus value to indicate thread is waiting on condition
         *
         * -- waitStatus值，指示线程正在等待条件
         */
        static final int CONDITION = -2;

        /**
         * waitStatus value to indicate the next acquireShared should
         * unconditionally propagate
         * 传播
         */
        //先不说...
        static final int PROPAGATE = -3;

        /**
         * Status field, taking on only the values:
         * SIGNAL:     The successor of this node is (or will soon be)
         * blocked (via park), so the current node must
         * unpark its successor when it releases or
         * cancels. To avoid races, acquire methods must
         * first indicate they need a signal,
         * then retry the atomic acquire, and then,
         * on failure, block.
         * CANCELLED:  This node is cancelled due to timeout or interrupt.
         * Nodes never leave this state. In particular,
         * a thread with cancelled node never again blocks.
         * CONDITION:  This node is currently on a condition queue.
         * It will not be used as a sync queue node
         * until transferred, at which time the status
         * will be set to 0. (Use of this value here has
         * nothing to do with the other uses of the
         * field, but simplifies mechanics.)
         * PROPAGATE:  A releaseShared should be propagated to other
         * nodes. This is set (for head node only) in
         * doReleaseShared to ensure propagation
         * continues, even if other operations have
         * since intervened.
         * 0:          None of the above
         *
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to
         * signal. So, most code doesn't need to check for particular
         * values, just for sign.
         *
         * The field is initialized to 0 for normal sync nodes, and
         * CONDITION for condition nodes.  It is modified using CAS
         * (or when possible, unconditional volatile writes).
         */
        //node状态，可选值（默认0 ， SIGNAl（-1）, CANCELLED（1）, CONDITION, PROPAGATE）
        // waitStatus == 0  默认状态
        // waitStatus > 0 取消状态
        // waitStatus == -1 表示当前node如果是head节点时，释放锁之后，需要唤醒它的后继节点。
        volatile int waitStatus;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking waitStatus. Assigned during enqueuing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         */
        //因为node需要构建成  fifo 队列， 所以 prev 指向 前继节点
        volatile Node prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned during enqueuing, adjusted
         * when bypassing cancelled predecessors, and nulled out (for
         * sake of GC) when dequeued.  The enq operation does not
         * assign next field of a predecessor until after attachment,
         * so seeing a null next field does not necessarily mean that
         * node is at end of queue. However, if a next field appears
         * to be null, we can scan prev's from the tail to
         * double-check.  The next field of cancelled nodes is set to
         * point to the node itself instead of null, to make life
         * easier for isOnSyncQueue.
         */
        //因为node需要构建成  fifo 队列， 所以 next 指向 后继节点
        volatile Node next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         */
        //当前node封装的 线程本尊..
        volatile Thread thread;

        /**
         * Link to next node waiting on condition, or the special
         * value SHARED.  Because condition queues are accessed only
         * when holding in exclusive mode, we just need a simple
         * linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the queue to
         * re-acquire. And because conditions can only be exclusive,
         * we save a field by using special value to indicate shared
         * mode.
         *
         * 条件队列中就是用的单向链表结构
         */
        Node nextWaiter;

        /**
         * Returns true if node is waiting in shared mode.
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * Returns previous node, or throws NullPointerException if null.
         * Use when predecessor cannot be null.  The null check could
         * be elided, but is present to help the VM.
         *
         * @return the predecessor of this node
         *
         * 前任
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null) {
                throw new NullPointerException();
            } else {
                return p;
            }
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    //end of Node class

    /**
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only via method setHead.  Note:
     * If head exists, its waitStatus is guaranteed not to be
     * CANCELLED.
     */
    //头结点 任何时刻 头结点对应的线程都是当前持锁线程。
    private transient volatile Node head;

    /**
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    //阻塞队列的尾节点   (阻塞队列不包含 头结点  head.next --->  tail 认为是阻塞队列)
    private transient volatile Node tail;

    /**
     * The synchronization state. 同步状态。
     * 不同模式下有不同的含义
     * 1.独占模式：0 表示未加锁状态   >0 表示已经加锁状态
     * 2.共享模式：
     */
    private volatile int state;

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a {@code volatile} read.
     *
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     *
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a {@code volatile} read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     * value was not equal to the expected value.
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities -- 排队工具

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices
     * to improve responsiveness with very short timeouts.
     *
     * 旋转超时阈值
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * Inserts node into queue, initializing if necessary. See picture above.
     * -- 将节点插入队列，必要时进行初始化
     *
     * @param node the node to insert
     * @return node's predecessor -- 节点的前身
     */
    //AQS#enq()
    //返回值：返回当前节点的 前置节点。
    private Node enq(final Node node) {
        //自旋入队，只有当前node入队成功后，才会跳出循环。
        for (; ; ) {
            Node t = tail;

            //initializing if necessary 必要时进行初始化
            if (t == null) { // Must initialize -- 必须初始化
                //1.当前队列是空队列  tail == null
                //说明当前 锁被占用，且当前线程 有可能是第一个获取锁失败的线程（当前时刻可能存在一批获取锁失败的线程...）

                //作为当前持锁线程的 第一个 后继线程，需要做什么事？
                //1.因为当前持锁的线程，它获取锁时，直接tryAcquire成功了，没有向 阻塞队列 中添加任何node，所以作为后继需要为它擦屁股..
                //2.然后在下一次自旋的时候为自己追加node

                if (compareAndSetHead(new Node())) {
                    //CAS成功，说明当前线程 成为head.next节点。
                    //线程需要为当前持锁的线程 创建head。
                    tail = head;
                }

                //注意：这里没有return,会继续for。。
            } else {
                //普通入队方式，只不过在for中，会保证一定入队成功！
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * Creates and enqueues node for current thread and given mode.
     * -- 为当前线程和给定模式创建并排队节点。
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node 最终返回当前线程包装出来的node
     */
    //AQS#addWaiter
    //最终返回当前线程包装出来的node
    private Node addWaiter(Node mode) {
        //Node.EXCLUSIVE
        //构建Node ，把当前线程封装到对象node中了
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure -- 尝试enq的快速路径；备份到完全失败
        //快速入队
        //获取队尾节点 保存到pred变量中
        Node pred = tail;
        //条件成立：队列中已经有node了
        if (pred != null) {
            //当前节点的prev 指向 pred
            node.prev = pred;
            //存在并发
            if (compareAndSetTail(pred, node)) {
                //cas成功，说明node入队成功

                //前置节点指向当前node，完成 双向绑定。
                pred.next = node;
                return node;
            }

            //cas 失败，说明node入队失败，继续往下走

        }
        //什么时候会执行到这里呢？
        //1.当前队列是空队列  tail == null
        //2.CAS竞争入队失败..会来到这里..

        //完整入队..
        enq(node);
        return node;
    }

    /**
     * Sets head of queue to be node, thus dequeuing.
     *
     * Called only by acquire methods.
     *
     * Also nulls out unused fields for sake of GC and to suppress unnecessary signals and traversals.
     *
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * Wakes up node's successor, if one exists.
     * -- 唤醒节点(参数node)的后继者（如果存在）。
     *
     * @param node the node
     * @see AbstractQueuedSynchronizer#release(int)
     */
    private void unparkSuccessor(Node node) {
        //获取当前节点的状态
        int ws = node.waitStatus;

        if (ws < 0) {//-1 Signal
            // 改成零的原因：因为当前节点已经完成喊后继节点的任务了..
            compareAndSetWaitStatus(node, ws, 0);
        }

        //s是当前节点 的第一个后继节点。
        Node s = node.next;

        //条件一：
        //s 什么时候等于null？
        //1.当前节点(参数node)就是tail节点时，tail的后继肯定没有罗，则  s == null。
        //2.当新节点入队未完成时（可以阅读 addWaiter 方法）
        //  2.1.设置新节点的prev 指向pred
        //  2.2.cas设置新节点为tail
        //  2.3.（未完成）pred.next -> 新节点
        //在2.2跟2.3之间的时候执行到这里，则 node.next 可能就是 null
        if (s == null

                //条件二：s.waitStatus > 0    前提：s ！= null
                //s.waitStatus > 0成立：说明 当前node节点的后继节点是 取消状态，当然不能唤醒
                //需要找一个合适的可以被唤醒的节点.
                || s.waitStatus > 0) {

            //查找可以被唤醒的节点...
            s = null;
            for (Node t = tail;//从后往前查询
                 t != null && t != node;//查询结束条件为t不为null，并且不是参数node
                 t = t.prev) {

                if (t.waitStatus <= 0) {
                    //不断替换跟node更远的节点
                    //最终的s是离node 最近的节点
                    s = t;
                }
            }

            //上面循环，会找到一个离当前node最近的一个可以被唤醒的node。 node 可能找不到  node 有可能是null、、
        }

        //如果找到合适的可以被唤醒的node，则唤醒.. 找不到 啥也不做。
        if (s != null) {

            //但是当前head并没有出队
            LockSupport.unpark(s.thread);
        }
    }

    /**
     * Release action for shared mode -- signals successor and ensures
     * propagation. (Note: For exclusive mode, release just amounts
     * to calling unparkSuccessor of head if it needs signal.)
     */
    private void doReleaseShared() {
        /*
         * Ensure that a release propagates, even if there are other
         * in-progress acquires/releases.  This proceeds in the usual
         * way of trying to unparkSuccessor of head if it needs
         * signal. But if it does not, status is set to PROPAGATE to
         * ensure that upon release, propagation continues.
         * Additionally, we must loop in case a new node is added
         * while we are doing this. Also, unlike other uses of
         * unparkSuccessor, we need to know if CAS to reset status
         * fails, if so rechecking.
         */
        for (; ; ) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
                        continue;            // loop to recheck cases
                    }
                    unparkSuccessor(h);
                } else if (ws == 0 &&
                        !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
                    continue;                // loop on failed CAS
                }
            }
            if (h == head)                   // loop if head changed
            {
                break;
            }
        }
    }

    /**
     * Sets head of queue, and checks if successor may be waiting
     * in shared mode, if so propagating if either propagate > 0 or
     * PROPAGATE status was set.
     *
     * @param node the node
     * @param propagate the return value from a tryAcquireShared
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // Record old head for check below
        setHead(node);
        /*
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
                (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared()) {
                doReleaseShared();
            }
        }
    }

    // Utilities for various versions of acquire

    /**
     * 取消指定node参与竞争。
     *
     * @see ReentrantLock#lockInterruptibly()
     * @see AbstractQueuedSynchronizer#doAcquireInterruptibly(int)
     */
    private void cancelAcquire(Node node) {
        if (node == null) {
            return;
        }
        //因为已经取消排队了..所以node内部关联的当前线程，置为Null就好了。。
        node.thread = null;
        //获取当前取消排队node的前驱。
        Node pred = node.prev;
        /**
         * @see Node#CANCELLED waitStatus > 0 说明也是取消状态
         */
        while (pred.waitStatus > 0) {
            node.prev = pred = pred.prev;
        }
        /**
         * 拿到前驱(pred 可能不是node的前驱了)的后继(也有可能不是当前节点了)节点。
         * 1.当前node: head -> -1 -> -1 -> -1(pred) -> node(predNext) -> ..... -> tail
         * 2.可能也是 ws > 0 的节点。head -> -1(pred) -> 1(predNext) -> 1 -> node -> ..... -> tail
         */
        Node predNext = pred.next;
        //将当前node状态设置为 取消状态  1
        /**
         * 1.当前node: head -> -1 -> -1 -> -1(pred) -> node(predNext)：1 -> ..... -> tail
         * 2.可能也是 ws > 0 的节点。head -> -1(pred) -> 1(predNext) -> 1 -> node：1 -> ..... -> tail
         */
        node.waitStatus = Node.CANCELLED;

        //此时pred节点定不是取消状态!!!!!!!!

        /**
         * 当前取消排队的node所在 队列的位置不同，执行的出队策略是不一样的，一共分为三种情况：
         * 1.当前node是队尾  tail -> node
         * 2.当前node 不是 head.next 节点，也不是 tail
         * 3.当前node 是 head.next节点。
         */
        //条件一：node == tail  成立：当前node是队尾  tail -> node
        if (node == tail//说明当前要出队的节点就是尾节点
                //条件二：compareAndSetTail(node, pred) 成功的话，说明修改tail完成。
                //cas成功之后tail指针就指向了pred
                && compareAndSetTail(node, pred)) {

            //说明要出队节点为tail节点，并且已经把tail指针指向了pred

            //修改pred.next -> null. 完成node出队。使之前的pred.next指针指向null,因为此时pred节点已经是tail了
            compareAndSetNext(pred, predNext, null);
        }

        //下面的情况是node不是tail的情况
        else {
            //保存节点状态
            int ws;

            //第二种情况：当前node 不是 head.next 节点，也不是 tail
            /**
             * head -> -1 -> -1 -> -1(pred) -> 1(predNext) -> 1 -> 1 -> node：1 -> ..... -> tail
             */
            if (pred != head//条件一：pred != head 成立， 说明当前node 不是 head.next 节点，也不是 tail(上面已经判断过了)

                    && (
                    //条件2.1：(ws = pred.waitStatus) == Node.SIGNAL
                    //成立：说明node的前驱状态是 Signal 状态
                    //不成立：前驱状态可能是0 ，极端情况下：前驱也取消排队了..
                    (ws = pred.waitStatus) == Node.SIGNAL
                            //条件2.2: 假设前驱状态是 <= 0 则设置前驱状态为 Signal状态..表示要唤醒后继节点。
                            || (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL)))

                    //基本都成立
                    && pred.thread != null) {
                //if里面做的事情，就是让pred.next 指向 node.next也就是跳过中间的所有状态为取消的节点,所以需要保证pred节点状态为 Signal状态。

                //出队：pred.next 指向 node.next 节点后，当node.next节点 被唤醒后调用 shouldParkAfterFailedAcquire 会让node.next 节点越过取消状态的节点完成真正出队。
                Node next = node.next;
                if (next != null && next.waitStatus <= 0) {
                    /**
                     * head -> -1 -> -1 -> -1(pred) -> 1(predNext) -> 1 -> 1 -> node：1 -> -1 -> ..... -> tail
                     * cas之后：
                     * head -> -1 -> -1 -> -1(pred) -> -1(next) -> ..... -> tail
                     */
                    compareAndSetNext(pred, predNext, next);
                }
            }

            //3.当前node 是 head.next节点。
            else {
                /**
                 * head(-1) -> node(1) -> -1 -> -1 -> tail
                 *
                 * 当前node 是 head.next节点。  更迷了...！！！！！！
                 * 类似情况2，后继节点唤醒后，会调用 shouldParkAfterFailedAcquire 会让node.next 节点越过取消状态的节点
                 * 队列的第三个节点 会 直接 与 head 建立 双重指向的关系：
                 * head.next -> 第三个node  中间就是被出队的head.next 第三个node.prev -> head
                 */
                unparkSuccessor(node);
            }

            //node.next指向自己
            node.next = node; // help GC
        }
    }

    /**
     * Checks and updates status for a node that failed to acquire.
     * -- 检查并更新无法获取的节点的状态。
     *
     * Returns true if thread should block. This is the main signal control in all acquire loops.
     * -- 如果线程应阻塞，则返回true。这是所有采集循环中的主要信号控制。
     *
     * Requires that pred == node.prev.
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     */
    /**
     * 总结：
     * 1.当前节点的前置节点是 取消状态 ，第一次来到这个方法时 会越过 取消状态的节点， 第二次 会返回true 然后park当前线程
     * 2.当前节点的前置节点状态是0，当前线程会设置前置节点的状态为 -1 ，第二次自旋来到这个方法时  会返回true 然后park当前线程.
     *
     * 参数一：pred 当前线程node的前置节点
     * 参数二：node 当前线程对应node
     * 返回值：boolean  true 表示当前线程需要挂起..
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        //获取前置节点的状态
        //waitStatus：0 默认状态 new Node() ； -1 Signal状态，表示当前节点释放锁之后会唤醒它的第一个后继节点； >0 表示当前节点是CANCELED状态
        int ws = pred.waitStatus;
        //条件成立：表示前置节点是个可以唤醒当前节点的节点，所以返回true ==> parkAndCheckInterrupt() park当前线程了..
        //TODO 普通情况下，第一次来到 sshouldParkAfterFailedAcquire ws 不会是 -1？？？？？
        if (ws == Node.SIGNAL) {
            return true;
        }

        //条件成立： >0 表示前置节点是CANCELED状态
        if (ws > 0) {
            //找爸爸的过程，条件是什么呢？ 前置节点的 waitStatus <= 0 的情况。
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            //找到好爸爸后，退出循环
            //隐含着一种操作，CANCELED状态的节点会被出队。！！！！！
            pred.next = node;
        } else {
            //当前node前置节点的状态就是 0 的这一种情况。
            //将当前线程node的前置node，状态强制设置为 SIGNAl，表示前置节点释放锁之后需要 喊醒我..
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * Convenience method to park and then check if interrupted
     * -- park 的便捷方法，然后检查是否中断
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt() {
        //park当前线程 将当前线程 挂起
        LockSupport.park(this);

        //唤醒后返回当前线程 是否为 中断信号 唤醒。，如果为true则说明是被中断唤醒的
        return Thread.interrupted();
    }

    /*
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     */

    /**
     * Acquires in exclusive uninterruptible mode for thread already in
     * queue. Used by condition wait methods as well as acquire.
     *
     * @param node the node
     * @param arg the acquire argument
     * @return {@code true} if interrupted while waiting -- {@code true}如果在等待时被打断
     */

    //acquireQueued 需要做什么呢？
    //1.当前节点有没有被park? 挂起？ 没有  ==> 则该方法需要进行挂起该线程的操作
    //2.唤醒之后的逻辑在哪呢？   ==> 则该方法需要处理该线程被唤醒之后的逻辑。

    //参数一：node 就是当前线程包装出来的node，且当前时刻 已经入队成功了..通过 addWaiter 方法入队
    //参数二：当前线程抢占资源成功后，设置state值时 会用到。
    final boolean acquireQueued(final Node node, int arg) {
        //true 表示当前线程抢占锁成功，普通情况下【lock】 当前线程早晚会拿到锁..
        //false 表示失败，需要执行出队的逻辑... （回头讲 响应中断的lock方法时再讲。）
        boolean failed = true;
        try {
            //当前线程是否被中断
            boolean interrupted = false;
            //自旋..
            for (; ; ) {
                //什么时候会执行这里？
                //1.进入for循环时 在线程尚未park前会执行
                //2.线程park之后 被唤醒后，也会执行这里...

                //获取当前节点的前置节点..
                final Node p = node.predecessor();

                //条件一成立：p == head  说明当前节点为head.next节点，head.next节点在任何时候 都有权利去争夺锁.
                if (p == head

                        //条件二：tryAcquire(arg)
                        //如果tryAcquire成功：说明head对应的线程 已经释放锁了，head.next节点对应的线程，正好获取到锁了..
                        //如果tryAcquire成功失败：说明head对应的线程  还没释放锁呢...head.next仍然需要被park。。
                        && tryAcquire(arg)) {

                    //拿到锁之后需要做什么？
                    //设置自己为head节点。
                    setHead(node);
                    //将上个线程对应的node的next引用置为null。协助老的head出队..
                    p.next = null; // help GC
                    //当前线程 获取锁 过程中..没有异常
                    failed = false;
                    //返回当前线程的中断标记..
                    return interrupted;
                }
                //shouldParkAfterFailedAcquire  这个方法是干嘛的？ 当前线程获取锁资源失败后，是否需要挂起呢？
                //返回值：true -> 当前线程需要 挂起    false -> 不需要..
                if (shouldParkAfterFailedAcquire(p, node)

                        //parkAndCheckInterrupt()  这个方法什么作用？ 挂起当前线程，并且唤醒之后 返回 当前线程的 中断标记
                        // （唤醒：1.正常唤醒 其它线程 unpark 2.其它线程给当前挂起的线程 一个中断信号..）
                        //该方法会阻塞
                        /**
                         * @see AbstractQueuedSynchronizer#unparkSuccessor
                         * 该方法会唤醒你哦，唤醒之后当前线程会继续往下执行,继续自旋
                         */
                        && parkAndCheckInterrupt()) {
                    //如果是中断唤醒
                    //interrupted 为 true 表示当前node对应的线程是被 中断信号唤醒的...
                    interrupted = true;
                }
            }
        } finally {
            if (failed) {
                //表示抢占锁失败，需要执行出队的逻辑... （回头讲 响应中断的lock方法时再讲。）
                cancelAcquire(node);
            }
        }
    }

    /**
     * Acquires in exclusive interruptible mode.
     * -- 在排他性可中断模式下获取。
     *
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg) throws InterruptedException {
        //入队
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }

                //没有拿到锁，则阻塞
                if (shouldParkAfterFailedAcquire(p, node)

                        //如果该条件也返回true，说明是中断了
                        && parkAndCheckInterrupt()) {

                    //直接抛出
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * Acquires in exclusive timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        nanosTimeout > spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * Acquires in shared uninterruptible mode.
     *
     * @param arg the acquire argument
     */
    private void doAcquireShared(int arg) {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted) {
                            selfInterrupt();
                        }
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * Acquires in shared interruptible mode.
     *
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
            throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * Acquires in shared timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        nanosTimeout > spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    // Main exported methods

    /**
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method {@link Lock#tryLock()}.
     *
     * <p>The default
     * implementation throws {@link UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     * passed to an acquire method, or is the value saved on entry
     * to a condition wait.  The value is otherwise uninterpreted
     * and can represent anything you like.
     * @return {@code true} if successful. Upon success, this object has
     * been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     * synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in exclusive
     * mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     * passed to a release method, or the current state value upon
     * entry to a condition wait.  The value is otherwise
     * uninterpreted and can represent anything you like.
     * @return {@code true} if this object is now in a fully released
     * state, so that any waiting threads may attempt to acquire;
     * and {@code false} otherwise.
     * @throws IllegalMonitorStateException if releasing would place this
     * synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     * passed to an acquire method, or is the value saved on entry
     * to a condition wait.  The value is otherwise uninterpreted
     * and can represent anything you like.
     * @return a negative value on failure; zero if acquisition in shared
     * mode succeeded but no subsequent shared-mode acquire can
     * succeed; and a positive value if acquisition in shared
     * mode succeeded and subsequent shared-mode acquires might
     * also succeed, in which case a subsequent waiting thread
     * must check availability. (Support for three different
     * return values enables this method to be used in contexts
     * where acquires only sometimes act exclusively.)  Upon
     * success, this object has been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     * synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in shared mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     * passed to a release method, or the current state value upon
     * entry to a condition wait.  The value is otherwise
     * uninterpreted and can represent anything you like.
     * @return {@code true} if this release of shared mode may permit a
     * waiting acquire (shared or exclusive) to succeed; and
     * {@code false} otherwise
     * @throws IllegalMonitorStateException if releasing would place this
     * synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if synchronization is held exclusively with
     * respect to the current (calling) thread.  This method is invoked
     * upon each call to a non-waiting {@link ConditionObject} method.
     * (Waiting methods instead invoke {@link #release}.)
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link ConditionObject} methods, so need
     * not be defined if conditions are not used.
     *
     * @return {@code true} if synchronization is held exclusively;
     * {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     * {@link #tryAcquire} but is otherwise uninterpreted and
     * can represent anything you like.
     * @see ReentrantLock.FairSync#lock() 该方法中就是调用这个方法
     *
     * 一直阻塞到获取到锁为止!!!!!!!!!
     */
    public final void acquire(int arg) {
        /**
         * 条件一：!tryAcquire 尝试获取锁 获取成功返回true  获取失败 返回false。
         * @see ReentrantLock.FairSync#tryAcquire(int)  子类自己实现
         */
        if (!tryAcquire(arg) &&

                //条件二：2.1：addWaiter 将当前线程封装成node入队
                //       2.2：acquireQueued 挂起当前线程   唤醒后相关的逻辑..
                //      acquireQueued 返回true 表示挂起过程中线程被中断唤醒过..  false 表示未被中断过..
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {

            //中断唤醒

            //再次设置中断标记位 true
            selfInterrupt();
        }
    }

    /**
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     * {@link #tryAcquire} but is otherwise uninterpreted and
     * can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (!tryAcquire(arg)) {
            doAcquireInterruptibly(arg);
        }
    }

    /**
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquire}, returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * {@link #tryAcquire} until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method {@link Lock#tryLock(long, TimeUnit)}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     * {@link #tryAcquire} but is otherwise uninterpreted and
     * can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquire(arg) ||
                doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * Releases in exclusive mode.
     * -- 以独占模式释放锁
     *
     * Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     *
     * @param arg the release argument.  This value is conveyed to
     * {@link #tryRelease} but is otherwise uninterpreted and
     * can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     * @see ReentrantLock#unlock() 重入锁解锁的时候会调用
     */
    //AQS#release方法
    //ReentrantLock.unlock() -> sync.release()【AQS提供的release】
    public final boolean release(int arg) {
        //尝试释放锁，tryRelease 返回true 表示当前线程已经
        // 完全!!!!!!!
        // 释放锁
        //返回false，说明当前线程尚未完全释放锁..
        if (tryRelease(arg)) {
            //完全释放锁！！！！！

            //回顾下head什么情况下会被创建出来？
            //当持锁线程未释放线程时，且持锁期间 有其它后续线程想要获取锁时(调用lock方法)，
            //后续线程发现获取不了锁，而且队列是空队列，此时后续线程会为当前持锁中的
            //线程 构建出来一个head节点，然后后续线程  会追加到 head 节点后面。

            Node h = head;

            //h != null成立，说明队列中的head节点已经初始化过了，ReentrantLock 在使用期间 发生过 多线程竞争了
            if (h != null
                    //上面为true才会到下面，也就是 h != null 为 true

                    //h.waitStatus != 0成立，说明当前head后面一定插入过node节点。
                    && h.waitStatus != 0) {

                //说明有线程在等待获取锁，唤醒 h 的后继节点.
                unparkSuccessor(h);
            }
            return true;
        }
        return false;
    }

    /**
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once {@link #tryAcquireShared},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireShared} until success.
     *
     * @param arg the acquire argument.  This value is conveyed to
     * {@link #tryAcquireShared} but is otherwise uninterpreted
     * and can represent anything you like.
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0) {
            doAcquireShared(arg);
        }
    }

    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     *
     * @param arg the acquire argument.
     * This value is conveyed to {@link #tryAcquireShared} but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (tryAcquireShared(arg) < 0) {
            doAcquireSharedInterruptibly(arg);
        }
    }

    /**
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquireShared}, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg the acquire argument.  This value is conveyed to
     * {@link #tryAcquireShared} but is otherwise uninterpreted
     * and can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquireShared(arg) >= 0 ||
                doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.
     *
     * @param arg the release argument.  This value is conveyed to
     * {@link #tryReleaseShared} but is otherwise uninterpreted
     * and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a {@code true} return does not guarantee that any
     * other thread will ever acquire.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there may be other threads waiting to acquire
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
                s.prev == head && (st = s.thread) != null) ||
                ((h = head) != null && (s = h.next) != null &&
                        s.prev == head && (st = s.thread) != null)) {
            return st;
        }

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null) {
                firstThread = tt;
            }
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * Returns true if the given thread is currently queued.
     *
     * <p>This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null) {
            throw new NullPointerException();
        }
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread == thread) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * {@code true}, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from {@link
     * #tryAcquireShared}) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
                (s = h.next) != null &&
                !s.isShared() &&
                s.thread != null;
    }

    /**
     * Queries whether any threads have been waiting to acquire longer
     * than the current thread.
     *
     * <p>An invocation of this method is equivalent to (but may be
     * more efficient than):
     * <pre> {@code
     * getFirstQueuedThread() != Thread.currentThread() &&
     * hasQueuedThreads()}</pre>
     *
     * <p>Note that because cancellations due to interrupts and
     * timeouts may occur at any time, a {@code true} return does not
     * guarantee that some other thread will acquire before the current
     * thread.  Likewise, it is possible for another thread to win a
     * race to enqueue after this method has returned {@code false},
     * due to the queue being empty.
     *
     * <p>This method is designed to be used by a fair synchronizer to
     * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
     * Such a synchronizer's {@link #tryAcquire} method should return
     * {@code false}, and its {@link #tryAcquireShared} method should
     * return a negative value, if this method returns {@code true}
     * (unless this is a reentrant acquire).  For example, the {@code
     * tryAcquire} method for a fair, reentrant, exclusive mode
     * synchronizer might look like this:
     *
     * <pre> {@code
     * protected boolean tryAcquire(int arg) {
     *   if (isHeldExclusively()) {
     *     // A reentrant acquire; increment hold count
     *     return true;
     *   } else if (hasQueuedPredecessors()) {
     *     return false;
     *   } else {
     *     // try to acquire normally
     *   }
     * }}</pre>
     *
     * @return {@code true} if there is a queued thread preceding the
     * current thread, and {@code false} if the current thread
     * is at the head of the queue or the queue is empty
     * @since 1.7
     */
    public final boolean hasQueuedPredecessors() {
        // The correctness of this depends on head being initialized
        // before tail and on head.next being accurate if the current
        // thread is first in queue.
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;
        return h != t &&
                ((s = h.next) == null || s.thread != Thread.currentThread());
    }

    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting to acquire
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null) {
                ++n;
            }
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null) {
                list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q = hasQueuedThreads() ? "non" : "";
        return super.toString() +
                "[State = " + s + ", " + q + "empty queue]";
    }

    // Internal support methods for Conditions

    /**
     * Returns true if a node, always one that was initially placed on a condition queue, is now waiting to reacquire on sync queue.
     * -- 如果一个节点（总是一个最初放置在条件队列中的节点）现在正在等待在同步队列上重新获取，则返回true。意思就是节点如果到了AQS的阻塞队列，则返回true
     *
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isOnSyncQueue(Node node) {

        //条件一：node.waitStatus == Node.CONDITION 条件成立，说明当前node一定是在条件队列，
        //因为signal方法迁移节点到 阻塞队列前，会将node的状态设置为 0
        if (node.waitStatus == Node.CONDITION

                //条件二：前置条件：node.waitStatus != Node.CONDITION   ===> 节点不在条件队列了
                // 1.node.waitStatus == 0 (表示当前节点已经被signal了)
                // 2.node.waitStatus == 1 （当前线程是未持有锁调用await方法..最终会将node的状态修改为 取消状态..）
                //node.waitStatus == 0 为什么还要判断 node.prev == null? 因为signal方法 是先修改状态，再迁移。
                || node.prev == null) {

            //说明还没有在AQS阻塞队列，仍然在条件队列中阻塞
            return false;
        }

        //执行到这里，会是哪种情况？
        //node.waitStatus != CONDITION 且 node.prev != null  ===> 可以排除掉 node.waitStatus == 1 取消状态..
        //为什么可以排除取消状态？ 因为 signal 方法是不会把 取消状态的node迁移走的
        //设置prev引用的逻辑 是 迁移 阻塞队列 逻辑的设置的（enq()）
        //入队的逻辑：1.设置node.prev = tail;   2. cas当前node为 阻塞队列的 tail 尾节点 成功才算是真正进入到 阻塞队列！ 3.pred.next = node;
        //可以推算出，就算prev不是null，也不能说明当前node 已经成功入队到 阻塞队列了。

        if (node.next != null) {
            //条件成立：说明当前节点已经成功入队到阻塞队列，且当前节点后面已经有其它node了...
            // If has successor, it must be on queue
            //因为 next 只有在AQS队列中赋值，在条件队列中的节点next引用都是null!!!!!!
            return true;
        }


        /*
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         * node.prev可以为非空，但尚未排队，因为将CAS放入队列的CAS可能会失败。
         * 因此，我们必须从尾巴开始遍历以确保它确实做到了。
         * 在此方法的调用中，它将始终靠近尾部，除非CAS失败（这不太可能），否则它将一直存在，因此我们几乎不会遍历太多。
         */

        //上面的所谓的高效方法实在找不到了，则用笨方法，直接去阻塞队列中进行查询

        /**
         * 执行到这里，说明当前节点的状态为：node.prev != null 且 node.waitStatus == 0
         * findNodeFromTail 从阻塞队列的尾巴开始向前遍历查找node，如果查找到 返回true,查找不到返回false
         * 当前node有可能正在signal过程中，正在迁移中...还未完成...
         */
        return findNodeFromTail(node);
    }

    /**
     * Returns true if node is on sync queue by searching backwards from tail.
     * -- 如果节点在同步队列中（从尾向后搜索），则返回true。
     *
     * Called only when needed by isOnSyncQueue.
     *
     * findNodeFromTail 从阻塞队列的尾巴开始向前遍历查找node，如果查找到 返回true,查找不到返回false
     *
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (; ; ) {
            if (t == node) {
                return true;
            }
            if (t == null) {
                return false;
            }
            t = t.prev;
        }
    }

    /**
     * 调用signal的是会调用当前方法
     * Transfers a node from a condition queue onto sync queue.
     * -- 将节点从条件队列转移到同步队列。
     *
     * Returns true if successful.
     *
     * @param node the node 计划迁移的节点
     * @return true if successfully transferred (else the node was cancelled before signal)
     * - 如果成功传输，则返回true（否则，节点在信号之前被取消的情况就是失败）
     */
    final boolean transferForSignal(Node node) {
        /**
         * If cannot change waitStatus, the node has been cancelled.
         * -- 如果无法更改waitStatus，则该节点已被取消。
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            /**
             * cas修改当前节点的状态，修改为0，因为当前节点马上要迁移到 阻塞队列了，所以得出一个结论：：：：迁移过去的节点状态都是0！
             *      成功：当前节点在条件队列中状态正常(为Node.CONDITION)。
             *      失败：
             *      1.取消状态 （线程await时 未持有锁，最终线程对应的node会设置为 取消状态），
             * @see AbstractQueuedSynchronizer#fullyRelease 在添加添加队列节点的时候，在该方法的finally代码块中失败的时候会修改状态为去取消
             *      2.node对应的线程 挂起期间，被其它线程使用 中断信号 唤醒过...（就会主队进入到 阻塞队列，这时也会修改状态为0）
             *
             * 总结：
             * signal的时候迁移节点，会把节点的状态设置为0
             * cas修改状态失败了，就会进来，说明在迁移之前节点的状态已经不是CONDITION了，极有可能是取消状态
             *
             * 进入该处的节点无非2个情况
             * 1.进入的时候就不是持锁线程，状态直接为取消，当前状态是1了
             * 2.在signal之前就已经被唤醒过了，当前状态已经是0了
             */
            return false;
        }

        //下面是cas成功的情况,此时当前节点状态是0

        /*
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         * -- 拼接到队列上并尝试设置前任的waitStatus来指示线程（可能）正在等待。
         * 如果取消或尝试设置waitStatus失败，请唤醒以重新同步（在这种情况下，waitStatus可能会暂时性且无害地错误）。
         */
        //enq最终会将当前 node 入队到 阻塞队列，p 是当前节点在阻塞队列的 前驱节点.
        Node p = enq(node);

        //ws 前驱节点的状态..
        int ws = p.waitStatus;

        if (ws > 0 //条件一：ws > 0 成立：说明前驱节点的状态在阻塞队列中是 取消状态,唤醒当前节点。

                //条件二：前置条件(ws <= 0)，
                //cas返回true 表示设置前驱节点状态为 SIGNAl状态成功
                //cas返回false  ===> 什么时候会false?
                //当前驱node对应的线程 是 lockInterrupt 入队的node时，是会响应中断的，外部线程给前驱线程中断信号之后，前驱node会将
                //状态修改为 取消状态，并且执行 出队逻辑..
                //TODO ??????
                || !compareAndSetWaitStatus(p, ws, Node.SIGNAL)) {

            //前驱节点状态 只要不是 0 或者 -1 那么，就唤醒当前线程。
            //唤醒当前node对应的线程...回头再说。
            LockSupport.unpark(node.thread);
        }
        return true;
    }

    /**
     * Transfers node, if necessary, to sync queue after a cancelled wait.
     * -- 当节点被取消后，如有必要，将节点传输到同步队列。
     *
     * Returns true if thread was cancelled before being signalled.
     * -- 如果线程在发出信号之前被取消，则返回true。
     *
     * @param node the node
     * @return true if cancelled before the node was signalled
     * @see ConditionObject#checkInterruptWhileWaiting(java.util.concurrent.locks.AbstractQueuedSynchronizer.Node)
     */
    final boolean transferAfterCancelledWait(Node node) {

        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            //条件成立：说明当前node一定是在 条件队列内，因为signal 迁移节点到阻塞队列时，会将节点的状态修改为0

            //中断唤醒的node也会被加入到 阻塞队列中！！
            enq(node);
            //true：表示是在条件队列内被中断的.
            return true;
        }

        //执行到这里有几种情况？
        //1.当前node已经被外部线程调用 signal 方法将其迁移到 阻塞队列内了。
        //2.当前node正在被外部线程调用 signal 方法将其迁移至 阻塞队列中 进行中状态..

        /**
         * If we lost out to a signal(), then we can't proceed until it finishes its enq().
         * -- 如果我们输给了signal（），那么直到它完成enq（）之前我们无法继续进行。
         *
         * Cancelling during an incomplete transfer is both rare and transient, so just spin.
         * -- 在不完全的传输过程中取消是很少见且短暂的，因此只需旋转即可。
         */
        while (!isOnSyncQueue(node)) {
            Thread.yield();
        }

        //false:表示当前节点被中断唤醒时 不在 条件队列了..
        //signal之后别中断
        return false;
    }

    /**
     * Invokes release with current state value;
     *
     * returns saved state.
     *
     * Cancels node and throws exception on failure.
     *
     * @param node the condition node for this wait
     * @return previous sync state
     */
    final int fullyRelease(Node node) {
        //完全释放锁是否成功，当failed失败时，说明当前线程是未持有锁调用 await方法的线程..（错误写法..）
        //假设失败，在finally代码块中 会将刚刚加入到 条件队列的 当前线程对应的node状态 修改为 取消状态
        //后继线程就会将 取消状态的 节点 给清理出去了..
        boolean failed = true;
        try {
            //获取当前线程 所持有的 state值 总数！
            //从AQS阻塞队列中拿！！！
            int savedState = getState();

            //绝大部分情况下：release 这里会返回true。
            if (release(savedState)) {
                //失败标记设置为false
                failed = false;
                //返回当前线程释放的state值
                //为什么要返回savedState？
                //因为在当你被迁移到“阻塞队列”后，再次被唤醒，且当前node在阻塞队列中是head.next 而且
                //当前lock状态是state == 0 的情况下，当前node可以获取到锁，此时需要将state 设置为savedState.
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed) {
                node.waitStatus = Node.CANCELLED;
            }
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     *
     * 其实就是一个单向链表！！！！！！！
     * 内部类可以访问外部对象的属性方法哦
     */
    public class ConditionObject implements Condition, java.io.Serializable {

        private static final long serialVersionUID = 1173984872572414699L;

        //明显是一个单向链表

        /**
         * First node of condition queue.
         */
        //指向条件队列的第一个node节点
        private transient Node firstWaiter;

        /**
         * Last node of condition queue.
         */
        //指向条件队列的最后一个node节点
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() {
        }

        // Internal methods

        /**
         * Adds a new waiter to wait queue.
         * -- 添加新的waiter 到等待队列。
         *
         * @return its new wait node
         * 调用await方法的线程 都是 持锁状态的，也就是说 addConditionWaiter 这里不存在并发！
         *
         * firstWaiter(node0：CONDITION) -> node1：!CONDITION -> node2：CONDITION -> lastWaiter(node3：CONDITION)
         * addConditionWaiter方法之后的条件队列为：
         * firstWaiter(node0：CONDITION) -> 被移除了 -> node2：CONDITION -> node3：CONDITION -> lastWaiter(newNode：CONDITION)
         */
        private Node addConditionWaiter() {
            //获取当前条件队列的尾节点的引用 保存到局部变量 t中
            Node t = lastWaiter;

            //条件一：t != null 成立：说明当前条件队列中，已经有node元素了..
            if (t != null

                    //条件二：node 在 条件队列中时，它的状态是 CONDITION（-2）
                    //     t.waitStatus != Node.CONDITION 成立：说明当前node发生中断了..
                    && t.waitStatus != Node.CONDITION) {

                //当前分支的目的： If lastWaiter is cancelled, clean out.（如果lastWaiter被取消，请清除。）

                //清理条件队列中所有取消状态的节点
                //该方法执行完成之后得到的队列都是条件节点了(node.waitStatus = Node.CONDITION)
                unlinkCancelledWaiters();
                //更新局部变量t 为最新队尾引用，因为上面unlinkCancelledWaiters可能会更改lastWaiter引用。
                t = lastWaiter;
            }

            //为当前线程创建node节点，设置状态为 CONDITION(-2),说明它是一个条件队列中的节点
            Node node = new Node(Thread.currentThread(), Node.CONDITION);

            /**************** 把新节点添加到条件队列的尾部 start *********************/
            if (t == null) {
                //条件成立：说明条件队列中没有任何元素，当前线程是第一个进入队列的元素。让firstWaiter 指向当前node
                firstWaiter = node;
            } else {
                //说明当前条件队列已经有其它node了 ，做追加操作
                t.nextWaiter = node;
            }
            //更新队尾引用指向 当前node。
            lastWaiter = node;
            /**************** 把新节点添加到条件队列的尾部 end *********************/

            //返回当前线程的node
            return node;
        }

        /**
         * Removes and transfers nodes until hit non-cancelled one or null.
         * -- 删除并转移节点，直到命中 非取消状态的节点 的一个或为null的节点。
         *
         * Split out from signal in part to encourage compilers to inline the case of no waiters.
         * -- 从信号中分离出来，部分鼓励编译器内联没有服务员的情况。
         *
         * @param first (non-null) the first node on condition queue
         *
         * firstWaiter -> node -> node -> node -> node -> lastWaiter
         *
         * 直至迁移某个节点成功，或者 条件队列为null为止。
         */
        private void doSignal(Node first) {
            do {
                //firstWaiter = first.nextWaiter 因为当前first马上要出条件队列了，
                //所以更新firstWaiter为 当前节点的下一个节点..
                //如果当前节点的下一个节点 是 null，说明条件队列只有当前一个节点了...当前出队后，整个队列就空了..
                //所以需要更新lastWaiter = null
                if ((firstWaiter = first.nextWaiter) == null) {
                    lastWaiter = null;
                }

                //当前first节点 出 条件队列。断开和下一个节点的关系.
                first.nextWaiter = null;

                //其实就是类似从头节点开始遍历，只是这里是出队，所以每次遍历都要断开连接
            } while (
                //意思就是迁移失败就继续迁移下一个
                    !transferForSignal(first)
                            //上一个迁移失败了，就迁移下一个，前提是还有下一个
                            && (first = firstWaiter) != null);
        }

        /**
         * Removes and transfers all nodes.
         * -- 删除并转移所有节点。
         *
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * -- 从条件队列中取消取消的服务员节点的链接。
         *
         * Called only while holding lock.
         * -- 仅在保持锁定状态下调用。
         *
         * This is called when cancellation occurred during condition wait, and upon insertion of a new waiter when lastWaiter is seen to have been cancelled.
         * -- 当在条件等待期间发生取消操作时，以及在插入新节点的时候看懂lastWaiter已经被取消的时候
         *
         * This method is needed to avoid garbage retention in the absence of signals.
         * -- 需要该方法来避免在没有信号的情况下保留垃圾。
         *
         * So even though it may require a full traversal, it comes into play only when timeouts or cancellations occur in the absence of signals.
         * -- 因此，即使可能需要完整遍历，它也只有在没有信号的情况下发生超时或取消时才起作用。
         *
         * It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation storms.
         * -- 它遍历所有节点，而不是停在特定目标上，以取消所有指向垃圾节点的指针的链接，而无需在取消风暴期间进行多次遍历。
         *
         *
         * 初始队列为：
         * firstWaiter -> CONDITION ->CONDITION -> !CONDITION ->CONDITION -> !CONDITION -> ...... -> lastWaiter
         *
         * 第一次遍历后：
         * firstWaiter -> CONDITION(trail)(t) ->CONDITION -> !CONDITION ->CONDITION -> !CONDITION -> ...... -> lastWaiter
         *
         * 第2次遍历后：
         * firstWaiter -> CONDITION ->CONDITION(trail)(t) -> !CONDITION ->CONDITION -> !CONDITION -> ...... -> lastWaiter
         *
         * 第3次遍历后：
         * firstWaiter -> CONDITION ->CONDITION(trail) -> !CONDITION(t) ->CONDITION(next) -> !CONDITION -> ...... -> lastWaiter
         *
         * firstWaiter -> CONDITION ->CONDITION(trail) -> CONDITION(t) -> !CONDITION -> ...... -> lastWaiter
         *
         * 总结：
         * firstWaiter ..... lastWaiter 的队列中，只要有状态不是CONDITION的节点都会被清除!!!!!
         */
        private void unlinkCancelledWaiters() {
            //表示循环当前节点，从链表的第一个节点开始 向后迭代处理.
            Node t = firstWaiter;
            //当前链表 上一个 正常状态的node节点
            Node trail = null;

            while (t != null) {
                //当前节点的下一个节点.
                Node next = t.nextWaiter;

                if (t.waitStatus != Node.CONDITION) {
                    //条件成立：说明当前节点状态为 取消状态，需要出队

                    //更新nextWaiter为null
                    t.nextWaiter = null;
                    //条件成立：说明遍历到的节点还未碰到过正常节点..
                    if (trail == null) {
                        //更新firstWaiter指针为下个节点就可以
                        firstWaiter = next;
                    } else {
                        //让上一个正常节点指向 取消节点的 下一个节点..中间有问题的节点 被跳过去了..
                        trail.nextWaiter = next;
                    }

                    //条件成立：当前节点为队尾节点了，更新lastWaiter 指向最后一个正常节点 就Ok了
                    if (next == null) {
                        lastWaiter = trail;
                    }
                } else {
                    //条件不成立执行到else，说明当前节点是正常节点
                    trail = t;
                }
                //继续遍历下一个节点
                t = next;
            }
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the wait queue for this condition to the wait queue for the owning lock.
         * -- 将等待时间最长的线程（如果存在）从该条件的等待队列移至拥有锁的等待队列。
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        public final void signal() {
            //判断调用signal方法的线程是否是独占锁持有线程，如果不是，直接抛出异常..
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }

            //获取条件队列的一个node
            Node first = firstWaiter;
            //第一个节点不为null，则将第一个节点 进行迁移到 阻塞队列的逻辑..
            if (first != null) {
                doSignal(first);
            }
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        public final void signalAll() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            Node first = firstWaiter;
            if (first != null) {
                doSignalAll(first);
            }
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted()) {
                    interrupted = true;
                }
            }
            if (acquireQueued(node, savedState) || interrupted) {
                selfInterrupt();
            }
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /**
         * Mode meaning to reinterrupt on exit from wait
         * -- 模式意味着在等待退出时重新中断
         */
        private static final int REINTERRUPT = 1;

        /**
         * Mode meaning to throw InterruptedException on exit from wait
         * -- 模式的意思是在退出等待时抛出InterruptedException
         */
        private static final int THROW_IE = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted before signalled, REINTERRUPT if after signalled, or 0 if not interrupted.
         * -- 检查是否有中断，如果在发出信号之前被中断，则返回THROW_IE；在发出信号之后，则返回REINTERRUPT；如果没有被中断，则返回0。
         */
        private int checkInterruptWhileWaiting(Node node) {

            return
                    //Thread.interrupted() 返回当前线程中断标记位，并且重置当前标记位 为 false 。
                    Thread.interrupted() ?

                            //transferAfterCancelledWait 这个方法只有在线程是被中断唤醒时 才会调用！
                            (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :

                            0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {

            if (interruptMode == THROW_IE) {
                //条件成立：说明在条件队列内发生过中断，此时await方法抛出中断异常
                throw new InterruptedException();
            } else if (interruptMode == REINTERRUPT) {
                //条件成立：说明在条件队列外发生的中断，此时设置当前线程的中断标记位 为true
                //中断处理 交给 你的业务处理。 如果你不处理，那什么事 也不会发生了...
                selfInterrupt();
            }
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         *
         * <li> If current thread is interrupted, throw InterruptedException.
         *
         * <li> Save lock state returned by {@link #getState}.
         *
         * <li> Invoke {@link #release} with saved state as argument, throwing IllegalMonitorStateException if it fails.
         *
         * <li> Block until signalled or interrupted.--- 阻塞直到 signalled 或被打断。!!!!!!!!
         *
         * <li> Reacquire by invoking specialized version of {@link #acquire} with saved state as argument.
         *
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         *
         * </ol>
         */
        public final void await() throws InterruptedException {
            //判断当前线程是否是中断状态，如果是则直接给个中断异常了..
            if (Thread.interrupted()) {
                //响应中断
                throw new InterruptedException();
            }

            //将调用await方法的线程包装成为node并且加入到条件队列中，并返回当前线程的node。之后的条件队列中的节点都是CONDITION状态
            Node node = addConditionWaiter();
            //完全释放掉当前线程对应的锁（将state置为0）
            //为什么要释放锁呢？  加着锁 挂起后，谁还能救你呢？
            //注意：只有拿到锁的线程才能调用 await() 方法！！所以这里释放资源是能够成功的
            int savedState = fullyRelease(node);

            /**
             * 0 ：  在condition队列挂起期间未接收过过中断信号
             * -1：  在condition队列挂起 期间！！ 接收到中断信号了
             * 1 ：  在condition队列挂起期间为未接收到中断信号，但是 迁移！！ 到“阻塞队列”之后 接收过中断信号。
             */
            int interruptMode = 0;

            /**
             * isOnSyncQueue(Node) 节点是否在AQS阻塞队列中？
             * 返回true：  表示当前线程对应的node已经迁移到 “阻塞队列” 了，到了AQS的阻塞队列了
             * 返回false： 说明当前node仍然还在 条件队列中，需要继续park！
             *
             * 前提：1.当前线程已经释放了锁资源
             *      2.当前线程在该condition的条件队列中了，但是目前还没有挂起
             *
             * 进入方法之后：
             *      1.大概率不在阻塞队列，所以进入while循环之后就会挂起当前线程
             *      2.然后等待其他线程在这个condition上(可以理解成同一个条件满足了)调用signal或者signalAll为止！
             */
            while (!isOnSyncQueue(node)) {//不是在syncQueue就是在conditionQueue呗

                //进入while代码块说明：当前node还没有进入AQS阻塞队列，还在condition条件队列中！

                /**
                 * 挂起当前node对应的线程。  接下来去看 signal 过程...
                 * @see ConditionObject#signal()
                 */
                LockSupport.park(this);
                //什么时候会被唤醒？都有几种情况呢？
                //1.常规路径：外部线程获取到lock之后，调用了 signal()方法 转移条件队列的头节点到 阻塞队列， 当这个节点获取到锁后，会唤醒。
                //2.转移至阻塞队列后，发现阻塞队列中的前驱节点状态 是 取消状态，此时会唤醒当前节点
                //3.当前节点挂起期间，被外部线程使用中断唤醒..

                //checkInterruptWhileWaiting ：就算在condition队列挂起期间 线程发生中断了，对应的node也会被迁移到 “阻塞队列”。
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {

                    //如果 interruptMode == 0 ,则最多再循环一次，
                    //因为 checkInterruptWhileWaiting 方法也会把中断node迁移到AQS阻塞队列中去，所以下次循环肯定也会退出
                    break;
                }

            }

            /**
             * 1.出了while循环，执行到这里，就说明 当前node已经迁移到 “阻塞队列”了
             * 2.说明有其他线程在相同condition上调用了signal或者signalAll方法
             * 3.当前线程可以争取资源，如果争取到了资源继续往下执行，否则会在acquireQueued方法中继续挂起
             */

            //跟之前的AQS普通阻塞队列一样，还是要竞争锁！
            if (
                //acquireQueued ：竞争队列的逻辑..
                //条件一：返回true 表示在阻塞队列中 被外部线程中断唤醒过..
                    acquireQueued(node, savedState)
                            //条件二：interruptMode != THROW_IE 成立，说明当前node在条件队列内 未发生过中断
                            && interruptMode != THROW_IE) {

                //设置interruptMode = REINTERRUPT
                interruptMode = REINTERRUPT;
            }

            //考虑下 node.nextWaiter != null 条件什么时候成立呢？
            //其实是node在条件队列内时 如果被外部线程 中断唤醒时，会加入到阻塞队列，但是并未设置nextWaiter = null。
            if (node.nextWaiter != null) {
                // clean up if cancelled
                //清理条件队列内取消状态的节点..
                unlinkCancelledWaiters();
            }

            //条件成立：说明挂起期间 发生过中断（1.条件队列内的挂起 2.条件队列之外的挂起）
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout) throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) {
                unlinkCancelledWaiters();
            }
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
            return deadline - System.nanoTime();
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) {
                unlinkCancelledWaiters();
            }
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) {
                unlinkCancelledWaiters();
            }
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given synchronization object.
         * -- 如果此条件是由给定的同步对象创建的，则返回true。
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    ++n;
                }
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null) {
                        list.add(t);
                    }
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private static final long stateOffset;

    private static final long headOffset;

    private static final long tailOffset;

    private static final long waitStatusOffset;

    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("next"));

        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * CAS head field. Used only by enq.
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private static final boolean compareAndSetWaitStatus(Node node, int expect, int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(Node node,
            Node expect,
            Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
