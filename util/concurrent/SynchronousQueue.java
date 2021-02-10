package util.concurrent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@linkplain BlockingQueue blocking queue} in which each insert
 * operation must wait for a corresponding remove operation by another
 * thread, and vice versa.
 * -- {@linkplain BlockingQueue阻塞队列}，其中每个插入操作必须等待另一个线程进行相应的删除操作，反之亦然。
 *
 * A synchronous queue does not have any internal capacity, not even a capacity of one.
 * -- 同步队列没有任何内部容量，甚至没有一个容量。
 *
 * You cannot {@code peek} at a synchronous queue because an element is only present when you try to remove it;
 * -- 您无法在同步队列中{@code peek}，因为仅当您尝试删除它时，该元素才存在。
 *
 * you cannot insert an element (using any method) unless another thread is trying to remove it;
 * -- 您不能插入元素（使用任何方法），除非另一个线程试图将其删除；
 *
 *
 * you cannot iterate as there is nothing to iterate.
 * -- 您无法迭代，因为没有要迭代的内容。
 *
 * The <em>head</em> of the queue is the element that the first queued inserting thread is trying to add to the queue;
 * -- 队列的<em> head </ em>是第一个排队的插入线程试图添加到队列中的元素；
 *
 * if there is no such queued thread then no element is available for removal and {@code poll()} will return {@code null}.
 * -- 如果没有这样的排队线程，则没有元素可用于删除，并且{@code poll（）}将返回{@code null}。
 *
 * For purposes of other {@code Collection} methods (for example {@code contains}), a {@code SynchronousQueue} acts as an empty collection.
 * -- 出于其他{@code Collection}方法（例如{@code contains}）的目的，{@code SynchronousQueue}表现得像空集合。
 *
 * This queue does not permit {@code null} elements.
 * -- 此队列不允许{@code null}元素。！！！！！
 *
 * <p>
 * Synchronous queues are similar to rendezvous channels used in CSP and Ada.
 * -- 同步队列类似于CSP和Ada中使用的集合通道。
 *
 * They are well suited for handoff designs, in which an object running in one thread must sync up with
 * an object running in another thread in order to hand it some information, event, or task.
 * -- 它们非常适合切换设计，在该设计中，在一个线程中运行的对象必须与在另一个线程中运行的对象同步，以便向其传递一些信息，事件或任务。
 *
 * <p>
 * This class supports an optional fairness policy for ordering
 * waiting producer and consumer threads.  By default, this ordering
 * is not guaranteed. However, a queue constructed with fairness set
 * to {@code true} grants threads access in FIFO order.
 * -- 意思就是该类支持公平跟非公平，默认是非公平
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <E> the type of elements held in this collection
 * @author Doug Lea and Bill Scherer and Michael Scott
 * @since 1.5
 */
@SuppressWarnings("all")
public class SynchronousQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {

    private static final long serialVersionUID = -3223113410248163686L;

    /*
     * This class implements extensions of the dual stack and dual
     * queue algorithms described in "Nonblocking Concurrent Objects
     * with Condition Synchronization", by W. N. Scherer III and
     * M. L. Scott.  18th Annual Conf. on Distributed Computing,
     * Oct. 2004 (see also
     * http://www.cs.rochester.edu/u/scott/synchronization/pseudocode/duals.html).
     *
     * The (Lifo) stack is used for non-fair mode, and the (Fifo)
     * queue for fair mode.
     * -- （Lifo）堆栈用于非公平模式，（Fifo）队列用于公平模式。
     *
     * The performance of the two is generally
     * similar. Fifo usually supports higher throughput under
     * contention but Lifo maintains higher thread locality in common
     * applications.
     * -- 两者的性能通常相似。 Fifo通常在竞争下支持更高的吞吐量，但是Lifo在常见应用程序中保持更高的线程局部性。
     *
     * A dual queue (and similarly stack) is one that at any given
     * time either holds "data" -- items provided by put operations,
     * or "requests" -- slots representing take operations, or is
     * empty.
     *
     * A call to "fulfill" (i.e., a call requesting an item
     * from a queue holding data or vice versa) dequeues a
     * complementary node.
     * -- 对“实现”的调用（即，从保存数据的队列中请求商品的调用，反之亦然）使互补节点出队。
     *
     * The most interesting feature of these
     * queues is that any operation can figure out which mode the
     * queue is in, and act accordingly without needing locks.
     * -- 这些队列最有趣的功能是，任何操作都可以弄清楚队列所处的模式，并且无需锁就可以采取相应的措施。
     *
     * Both the queue and stack extend abstract class Transferer
     * defining the single method transfer that does a put or a
     * take.
     * -- 队列和堆栈都扩展了抽象类Transferer，它们定义了执行放置或取出操作的单个方法。
     *
     * These are unified into a single method because in dual
     * data structures, the put and take operations are symmetrical(对称的),
     * so nearly all code can be combined.
     * -- 将它们统一为一个方法，因为在双重数据结构中，放置和取出操作是对称的，因此几乎所有代码都可以合并。
     *
     * The resulting transfer
     * methods are on the long side, but are easier to follow than
     * they would be if broken up into nearly-duplicated parts.
     * --TODO 最终的传输方法长远来看，但比分解成几乎重复的部分要容易得多。????
     *
     * The queue and stack data structures share many conceptual
     * similarities but very few concrete details.
     * -- 队列和堆栈数据结构在概念上有很多相似之处，但具体细节很少。
     *
     * For simplicity,
     * they are kept distinct so that they can later evolve
     * separately.
     * -- 为简单起见，它们保持不同，以便以后可以分别发展。
     *
     * The algorithms here differ from the versions in the above paper
     * in extending them for use in synchronous queues, as well as
     * dealing with cancellation. The main differences include:
     *
     *  1. The original algorithms used bit-marked pointers, but
     *     the ones here use mode bits in nodes, leading to a number
     *     of further adaptations. -- 原始算法使用带位标记的指针，但是这里的算法使用节点中的模式位，从而导致了许多进一步的适应。
     *
     *  2. SynchronousQueues must block threads waiting to become
     *     fulfilled. -- SynchronousQueues必须阻塞等待fulfilled的线程。
     *
     *  3. Support for cancellation via timeout and interrupts,
     *     including cleaning out cancelled nodes/threads
     *     from lists to avoid garbage retention and memory depletion.
     * -- 支持通过超时和中断进行取消，包括从列表中清除已取消的节点/线程，以避免垃圾保留和内存耗尽
     *
     * Blocking is mainly accomplished using LockSupport park/unpark,
     * except that nodes that appear to be the next ones to become
     * fulfilled first spin a bit (on multiprocessors only).
     * -- 阻塞主要使用LockSupport暂存/取消暂存来完成，除了看起来像是首先要实现的下一个要暂存的节点外，它还会旋转一点（仅在多处理器上）。
     *
     * On very
     * busy synchronous queues, spinning can dramatically improve
     * throughput. -- 在非常繁忙的同步队列上，旋转可以大大提高吞吐量。
     *
     * And on less busy ones, the amount of spinning is
     * small enough not to be noticeable.-- 在不那么忙碌的纺纱厂上，纺纱的量很小，不足以引起注意。
     *
     * Cleaning is done in different ways in queues vs stacks.
     * -- 在队列和堆栈中以不同的方式进行清理
     *
     * For
     * queues, we can almost always remove a node immediately in O(1)
     * time (modulo retries for consistency checks) when it is
     * cancelled. But if it may be pinned as the current tail, it must
     * wait until some subsequent cancellation.
     * -- 对于队列，我们​​几乎总是可以在取消节点后的O（1）时间内立即删除该节点（进行模数重试以进行一致性检查）。但是，如果可能将其固定为当前尾巴，则必须等待直到随后的一些取消。
     *
     * For
     * stacks, we need a
     * potentially O(n) traversal to be sure that we can remove the
     * node, but this can run concurrently with other threads
     * accessing the stack.
     * -- 对于堆栈，我们需要潜在的O（n）遍历，以确保可以删除节点，但这可以与其他访问堆栈的线程同时运行
     *
     * While garbage collection takes care of most node reclamation
     * issues that otherwise complicate nonblocking algorithms, care
     * is taken to "forget" references to data, other nodes, and
     * threads that might be held on to long-term by blocked
     * threads. In cases where setting to null would otherwise
     * conflict with main algorithms, this is done by changing a
     * node's link to now point to the node itself. This doesn't arise
     * much for Stack nodes (because blocked threads do not hang on to
     * old head pointers), but references in Queue nodes must be
     * aggressively forgotten to avoid reachability of everything any
     * node has ever referred to since arrival.
     * -- 尽管垃圾回收会处理大多数会使非阻塞算法复杂化的节点回收问题，但还是要小心“忘记”对数据，
     * 其他节点和可能被阻塞线程长期保留的线程的引用。
     * 如果设置为null会与主要算法冲突，则可以通过将节点的链接更改为现在指向节点本身来完成。
     * 对于Stack节点，这不会发生太多（因为阻塞的线程不会挂在旧的头部指针上），但是必须积极地忘记Queue节点中的引用，以防止自到达以来任何节点都曾引用的所有内容都可以访问。
     */

    /**
     * Shared internal API for dual stacks and queues.
     * -- 双重堆栈和队列的共享内部API。
     */
    abstract static class Transferer<E> {

        /**
         * Performs a put or take.
         * put跟get/take操作都通过实现该接口做到
         *
         * @param e if non-null, the item to be handed to a consumer;
         * if null, requests that transfer return an item -- 可以为null，null时表示这个请求是一个 REQUEST 类型的请求，如果不是null，说明这个请求是一个 DATA 类型的请求。
         * offered by producer.
         * @param timed if this operation should timeout-- 如果为true 表示指定了超时时间 ,如果为false 表示不支持超时，表示当前请求一直等待到匹配为止，或者被中断。
         * @param nanos the timeout, in nanoseconds -- 超时时间限制 单位 纳秒
         * @return E 如果当前请求是一个 REQUEST类型的请求，返回值如果不为null 表示 匹配成功，如果返回null，表示REQUEST类型的请求超时 或 被中断。
         * 如果当前请求是一个 DATA 类型的请求，返回值如果不为null 表示 匹配成功，返回当前线程put的数据。
         * 如果返回值为null 表示，DATA类型的请求超时 或者 被中断..都会返回Null。
         */
        abstract E transfer(E e, boolean timed, long nanos);
    }

    /**
     * 为什么需要自旋这个操作？
     * 因为线程 挂起 唤醒站在cpu角度去看的话，是非常耗费资源的，涉及到用户态和内核态的切换...
     * 自旋的好处，自旋期间线程会一直检查自己的状态是否被匹配到，如果自旋期间被匹配到，那么直接就返回了
     * 如果自旋期间未被匹配到，自旋次数达到某个指标后，还是会将当前线程挂起的...
     * NCPUS：当一个平台只有一个CPU时，你觉得还需要自旋么？
     * 答：肯定不需要自旋了，因为一个cpu同一时刻只能执行一个线程，自旋没有意义了...而且你还占着cpu 其它线程没办法执行..这个
     * 栈的状态更不会改变了.. 当只有一个cpu时 会直接选择 LockSupport.park() 挂起等待者线程。
     */

    /**
     * The number of CPUs, for spin control
     */
    //表示运行当前程序的平台，所拥有的CPU数量
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    /**
     * The number of times to spin before blocking in timed waits.
     * The value is empirically derived -- it works well across a
     * variety of processors and OSes. Empirically, the best value
     * seems not to vary with number of CPUs (beyond 2) so is just
     * a constant.
     */
    //表示指定超时时间的话，当前线程最大自旋次数。
    //只有一个cpu 自旋次数为0
    //当cpu大于1时，说明当前平台是多核平台，那么指定超时时间的请求的最大自旋次数是 32 次。
    //32是一个经验值。
    static final int maxTimedSpins = (NCPUS < 2) ? 0 : 32;

    /**
     * The number of times to spin before blocking in untimed waits.
     * This is greater than timed value because untimed waits spin
     * faster since they don't need to check times on each spin.
     */
    //表示未指定超时限制的话，线程等待匹配时，自旋次数。
    //是指定超时限制的请求的自旋次数的16倍.
    static final int maxUntimedSpins = maxTimedSpins * 16;//0或者512次

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices.
     * -- 旋转秒级比使用定时停泊更快的纳秒数。粗略的估计就足够了。
     *
     * 如果请求是指定超时限制的话，如果超时nanos参数是< 1000 纳秒时，
     * 禁止挂起。挂起再唤醒的成本太高了..还不如选择自旋空转呢...
     */
    static final long spinForTimeoutThreshold = 1000L;

    /** Dual stack */
    /**
     * 双端栈
     * 非公平模式实现的同步队列，内部数据结构是 “栈”
     */
    static final class TransferStack<E> extends Transferer<E> {
        /*
         * This extends Scherer-Scott dual stack algorithm, differing,
         * among other ways, by using "covering" nodes rather than
         * bit-marked pointers: Fulfilling operations push on marker
         * nodes (with FULFILLING bit set in mode) to reserve a spot
         * to match a waiting node.
         * -- 这扩展了Scherer-Scott双栈算法，除其他方式外，它通过使用“覆盖”节点而不是位标记的指针而有所不同：实现操作将推送标记节点（将FULFILLING位设置为模式）以保留一个点以匹配等待节点。
         */

        /* Modes for SNodes, ORed together in node fields -- SNode的模式，在节点字段中一起或运算 */
        /** Node represents an unfulfilled consumer */
        /**
         * 表示Node类型为 请求类型
         * 二进制： ...... 0000 0000 0000 0000
         */
        static final int REQUEST = 0;
        /** Node represents an unfulfilled producer */
        /**
         * 表示Node类型为 数据类型
         * 二进制： ...... 0000 0000 0000 0001
         */
        static final int DATA = 1;
        /** Node is fulfilling another unfulfilled DATA or REQUEST */
        /**
         * 表示Node类型为 匹配中类型
         * 假设栈顶元素为 REQUEST 类型的节点，当前请求类型为 DATA类型的话，入栈会修改类型为 FULFILLING 【栈顶 & 栈顶之下的一个node】。
         * 假设栈顶元素为 DATA 类型的节点，当前请求类型为 REQUEST类型的话，入栈会修改类型为 FULFILLING 【栈顶 & 栈顶之下的一个node】。
         *
         * 二进制： ...... 0000 0000 0000 0010
         */
        static final int FULFILLING = 2;

        /**
         * Returns true if m has fulfilling bit set.
         */
        //判断当前模式是否为 匹配中状态。
        static boolean isFulfilling(int m) {
            /**
             * XX & 10 == 0
             * 则XX可能为：
             * 01
             *
             *
             *
             *
             *TODO
             *
             */
            return (m & FULFILLING) != 0;
        }

        /**
         * Node class for TransferStacks.
         *
         * 栈    顶
         *
         * ｜    ｜
         * ｜————｜
         * ｜    ｜
         * ｜————｜
         * ｜    ｜
         * ｜————｜
         * ｜    ｜
         * ｜————｜
         */
        static final class SNode {

            //指向下一个栈帧
            volatile SNode next;        // next node in stack

            //与当前node匹配的节点
            volatile SNode match;       // the node matched to this

            /**
             * 假设当前node对应的线程 自旋期间未被匹配成功，那么node对应的线程需要挂起，挂起前 waiter 保存对应的线程引用，
             * 方便 匹配成功后，被唤醒。
             *
             * 因为挂起的时候需要使用LockSupport.park跟unpark就需要使用线程，自旋是不需要的
             */
            volatile Thread waiter;     // to control park/unpark

            /**
             * 数据;或对于REQUEST为null -- data; or null for REQUESTs
             *
             * 数据域，
             *
             * data不为空 表示当前Node对应的请求类型为 DATA类型。
             *
             * 反之则表示Node为 REQUEST类型。
             */
            Object item;

            /**
             * 表示当前Node的模式 【DATA/REQUEST/FULFILLING】0/1/2
             */
            int mode;

            // Note: item and mode fields don't need to be volatile
            // since they are always written before, and read after,
            // other volatile/atomic operations.

            SNode(Object item) {
                this.item = item;
            }

            /**
             * CAS方式设置Node对象的next字段。
             *
             * @param cmp 比较对象
             * @param val 需要设置的值
             * @return 成功失败
             */
            boolean casNext(SNode cmp, SNode val) {
                /**
                 * CAS:comxchag LL/LS
                 *
                 * 优化：cmp == next  为什么要判断？
                 * 因为cas指令 在平台执行时，同一时刻只能有一个cas指令被执行。
                 * 有了java层面的这一次判断，可以提升一部分性能。
                 *
                 * 如果cmp != next 就没必要走 cas指令。
                 */
                return cmp == next && UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            /**
             * 栈    顶
             * ｜ F  ｜
             * ｜————｜
             * ｜ D  ｜
             * ｜————｜
             * ｜    ｜
             * ｜————｜
             * ｜    ｜
             * ｜————｜
             *
             * 其实是类型为D的对象调用该方法
             *
             * Tries to match node s to this node, if so, waking up thread.
             * Fulfillers call tryMatch to identify their waiters.
             * Waiters block until they have been matched.
             *
             * -- 尝试将节点s与此节点匹配，如果是，则唤醒线程。 Fulfiller呼叫tryMatch来识别其服务员。服务员封锁直到他们被匹配。
             *
             * 尝试匹配：调用该方法的对象是栈顶节点的下一个节点，与栈顶匹配的节点
             *
             * @param s the node to match 被匹配的节点
             * @return ture 匹配成功。 否则匹配失败..
             */
            boolean tryMatch(SNode s) {
                //条件一：match == null 成立，说明当前Node尚未与任何节点发生过匹配...
                if (match == null &&
                        //条件二 在条件一成立的前提下，如果成立：使用CAS方式 设置match字段，表示当前Node已经被匹配了
                        UNSAFE.compareAndSwapObject(this, matchOffset, null, s)) {

                    //当前Node如果自旋结束，那么会使用LockSupport.park 方法挂起，挂起之前会将Node对应的Thread 保留到 waiter字段。

                    Thread w = waiter;
                    if (w != null) {    // waiters need at most one unpark
                        //条件成立：说明Node对应的Thread已经挂起了...

                        waiter = null;

                        //使用unpark方式唤醒。因为匹配到了
                        LockSupport.unpark(w);
                    }
                    return true;
                }
                return match == s;
            }

            /**
             * Tries to cancel a wait by matching node to itself.
             * 尝试取消..
             */
            void tryCancel() {
                /**
                 * match字段 保留当前Node对象本身，表示这个Node是取消状态，取消状态的Node，最终会被 强制 移除出栈。
                 *
                 * 这个骚操作：把当前对象的match属性指向自己
                 *
                 * this.match = this;
                 *
                 * 目的是什么？因为取消的时候不能把当前对象设置为null,所以出此下策!
                 */
                UNSAFE.compareAndSwapObject(this, matchOffset, null, this);
            }

            /**
             * 如果match保留的是当前Node本身，那表示当前Node是取消状态，反之 则 非取消状态。
             *
             * @return
             */
            boolean isCancelled() {
                return match == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;

            private static final long matchOffset;

            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = SNode.class;
                    matchOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("match"));
                    nextOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }

        /**
         * The head (top) of the stack
         * 栈数据结构就只又一个栈顶节点引用就好了
         *
         * 表示栈顶指针，有栈顶就能操作这个栈了
         */
        volatile SNode head;

        /**
         * 设置栈顶元素
         *
         * @param h 老的head
         * @param nh 新的head
         * @return 成功失败
         */
        boolean casHead(SNode h, SNode nh) {
            return h == head//该条件成立才会cas
                    && UNSAFE.compareAndSwapObject(this, headOffset, h, nh);
        }

        /**
         * Creates or resets fields of a node. Called only from transfer
         * where the node to push on stack is lazily created and
         * reused when possible to help reduce intervals between reads
         * and CASes of head and to avoid surges of garbage when CASes
         * to push nodes fail due to contention.
         * -- 创建或重置节点的字段。仅从传输中调用，在该传输中延迟创建要推送到栈上的节点，并在可能时进行重用，以帮助减少读取和头的CASes之间的间隔，并避免当CASes推送到节点由于争用而失败时产生的大量垃圾。
         *
         * @param s SNode引用，当这个引用指向空时，snode方法会创建一个SNode对象 并且赋值给这个引用
         * @param e SNode对象的item字段
         * @param next 指向当前栈帧的下一个栈帧
         * @param mode REQUEST/DATA/FULFILLING
         */
        static SNode snode(SNode s, Object e, SNode next, int mode) {
            if (s == null) {
                s = new SNode(e);
            }
            s.mode = mode;
            s.next = next;
            return s;
        }

        /**
         * Puts or takes an item.
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            /*
             * Basic algorithm is to loop trying one of three actions:
             * -- 基本算法是循环尝试以下三个动作之一：
             *
             * 1. If apparently empty or already containing nodes of same
             *    mode, try to push node on stack and wait for a match,
             *    returning it, or null if cancelled.
             * -- 如果显然是空的或已经包含相同模式的节点，请尝试将节点压入堆栈并等待匹配，然后将其返回；如果取消，则返回null。
             *
             * 2. If apparently containing node of complementary mode,
             *    try to push a fulfilling node on to stack, match
             *    with corresponding waiting node, pop both from
             *    stack, and return matched item. The matching or
             *    unlinking might not actually be necessary because of
             *    other threads performing action 3:
             * -- 如果显然包含互补模式的节点，请尝试将满足条件的节点推入堆栈，与相应的等待节点匹配，从堆栈中弹出两者，然后返回匹配项。由于其他线程正在执行操作3，因此实际上可能不需要匹配或取消链接：
             *
             * 3. If top of stack already holds another fulfilling node,
             *    help it out by doing its match and/or pop
             *    operations, and then continue. The code for helping
             *    is essentially the same as for fulfilling, except
             *    that it doesn't return the item.
             * -- 如果堆栈顶部已经包含另一个充实的节点，请通过执行其匹配和/或弹出操作来对其进行帮助，然后继续。帮助代码与实现代码基本相同，不同之处在于它不返回项目。
             */

            //包装当前线程的Node
            SNode s = null; // constructed/reused as needed

            int mode = (e == null) ?
                    REQUEST : //e == null 条件成立：当前线程是一个REQUEST线程。
                    DATA;//否则 e!=null 说明 当前线程是一个DATA线程，提交数据的线程。

            //自旋
            for (; ; ) {
                //h 表示栈顶指针
                SNode h = head;

                /**
                 *  栈    顶
                 *
                 *  ｜ D  ｜
                 *  ｜————｜
                 *  ｜ D  ｜
                 *  ｜————｜
                 *  ｜ D  ｜
                 *  ｜————｜
                 *  ｜ D  ｜
                 *  ｜————｜
                 *
                 *  或者
                 *
                 *  栈    顶
                 *
                 *
                 *  ｜    ｜
                 *  ｜————｜
                 */
                //CASE1：当前栈内为空 或者 栈顶Node模式与当前请求模式一致，都是需要做入栈操作。
                if (h == null //当前栈内为空
                        //栈顶Node模式与当前请求模式一致
                        || h.mode == mode) {
                    //empty or same-mode

                    if (timed//条件一：成立，说明当前请求是指定了 超时限制的

                            //条件二：nanos <= 0 ,小于零基本不存在，所以如果 nanos == 0. 表示这个请求 不支持 “阻塞等待”。 queue.offer();
                            && nanos <= 0) {      // can't wait

                        if (h != null && h.isCancelled()) {
                            //条件成立：说明栈顶已经取消状态了，协助栈顶出栈。
                            // pop cancelled node
                            casHead(h, h.next);
                        } else {
                            /**
                             * CASE1的情况大部分情况从这里返回
                             *
                             * 因CASE表示栈为空或者当前请求模式跟栈顶模式一致
                             * 然而当前请求又不支持等待，那么只能返回null拉
                             */
                            return null;
                        }

                    }

                    /**
                     * 什么时候执行else if 呢？
                     * 1.当前栈顶为空 或者 模式与当前请求一致，
                     * 2.且当前请求允许 阻塞等待。
                     * TODO:timed=false好像也会执行到这里吧？
                     * casHead(h, s = snode(s, e, h, mode))  入栈操作。
                     */
                    else if (
                        //CAS替换老的头节点,新头节点s.next=老的头节点，其实就是试图压栈
                            casHead(h,//老的头节点

                                    //给当前请求创建了一个节点s,并且成为新的头节点
                                    s = snode(s, e, h, mode))) {
                        /**
                         * 执行到这里，说明 当前请求入栈成功。
                         *
                         * ｜ s  ｜ 新的头节点
                         * ｜————｜
                         * ｜ h  ｜ 老的头节点
                         * ｜————｜
                         * 入栈成功之后要做什么呢？：在栈内等待一个好消息，等待被匹配！
                         *
                         * awaitFulfill 等待被匹配的逻辑...
                         * 1.正常情况：返回匹配的节点
                         * 2.取消情况：返回当前节点  s节点进去，返回s节点...
                         */
                        SNode m = awaitFulfill(s,//s为当前请求的节点，已经压栈成功啦，可以等待匹配
                                timed, nanos);

                        //执行到这里，说明上面的阻塞方法已经完成，继续往下执行了，可能匹配成功，也可能失败
                        //m为与当前节点匹配的节点

                        if (m == s) {
                            // wait was cancelled
                            //条件成立：说明当前Node状态是 取消状态...

                            //将取消状态的节点 出栈...
                            clean(s);
                            //取消状态 最终返回null
                            return null;
                        }
                        //执行到这里 说明当前Node已经被匹配成功了

                        if ((h = head) != null//条件一：成立，说明栈顶是有Node(栈顶发生了变化)

                                //条件二：成立，说明 Fulfill 和 当前Node 还未出栈，需要协助出栈。
                                && h.next == s) {
                            /**
                             *  ｜ F  ｜
                             *  ｜————｜
                             *  ｜ s  ｜
                             *  ｜————｜
                             *  ｜s.n ｜
                             *  ｜————｜
                             *
                             *  说明与s匹配的请求来了，成功匹配了
                             *  此时协助栈顶跟h.next两个节点出栈,出栈之后：
                             *
                             *  ｜ s.n｜
                             *  ｜————｜
                             */
                            //将fulfill 和 当前Node 结对 出栈
                            casHead(h, s.next);     // help s's fulfiller
                        }

                        return (E) ((mode == REQUEST) ?
                                m.item ://当前NODE模式为REQUEST类型：返回匹配节点的m.item 数据域
                                s.item);//当前NODE模式为DATA类型：返回Node.item 数据域，当前请求提交的 数据e
                    }
                }

                /**
                 *  栈    顶
                 *
                 *  当前类型为R
                 *
                 *  ｜ D  ｜
                 *  ｜————｜
                 * 什么时候来到这？？
                 * 栈顶Node的模式与当前请求的模式不一致，会执行else if 的条件。
                 * 栈顶是          请求模型
                 * (DATA            REQUEST)
                 * (REQUEST         DATA)
                 * (FULFILLING      REQUEST/DATA)
                 * 会执行到这里
                 *
                 * CASE2：当前栈顶模式与请求模式不一致，且栈顶不是FULFILLING
                 */
                else if (!isFulfilling(h.mode)) { // try to fulfill
                    /**
                     * 来到这里的前提：
                     * 1.当前头节点 != null
                     * 2.并且当前头节点的模式跟当前请求节点的模式不桶
                     * 3.并且当前头节点的模式不是F类型
                     *
                     * 可以推断出以下几种情况
                     * h        s
                     * R        D
                     * D        R
                     */
                    if (h.isCancelled()) {
                        //条件成立：说明当前栈顶状态为 取消状态，当前线程协助它出栈。

                        // already cancelled
                        //协助 取消状态节点 出栈。
                        // pop and retry
                        casHead(h, h.next);
                    }

                    /**
                     * 前提：
                     * 1.当前头节点没有被取消
                     * 2.
                     * h        s
                     * R        D
                     * D        R
                     */
                    else if (casHead(h,

                            /**
                             * mode ： 当前请求的模式
                             * FULFILLING | mod 存在的几种情况：
                             * F | DATA = 0010 | 0001 === > 0011 则mode为3
                             * F | REQUEST = 0010 | 0000 === > 0010 则mode为2
                             */
                            s = snode(s, e, h, FULFILLING | mode))) {
                        //条件成立：说明压栈节点成功，入栈一个 FULFILLING | mode  NODE

                        /**
                         * 当前请求入栈成功
                         *
                         * ｜F｜R｜   当前头节点就是s，模式为R
                         * ｜————｜
                         * ｜D   ｜   与s匹配的节点:s.next,模式为D
                         * ｜————｜
                         *
                         * 开始自旋，fulfill 节点 和 fulfill.next 节点进行匹配工作...
                         */
                        for (; ; ) { // loop until matched or waiters disappear
                            //m 与当前s 匹配节点。
                            SNode m = s.next;       // m is s's match

                            /**
                             * m == null 什么时候可能成立呢？
                             * 当s.next节点 超时或者被外部线程中断唤醒后，
                             * 会执行 clean 操作 将 自己清理出栈，此时
                             * 站在匹配者线程 来看，真有可能拿到一个null。
                             *
                             * ｜F｜R｜   当前头节点就是s，模式为R
                             * ｜————｜
                             * ｜D   ｜   与s匹配的节点:s.next,模式为D，此时该节点对应的线程可能在自旋也可能在挂起
                             * ｜————｜
                             *
                             * 如果m==null,则
                             * ｜F｜R｜   当前头节点就是s，模式为R
                             * ｜————｜
                             *
                             *  因为D节点对应的线程在自旋或者挂起结束之后超时了或者中断了，此时就会把自己给取消
                             *  然后进行clean操作，所以才会导致这个情况的出现！！！！
                             */
                            if (m == null) {        // all waiters are gone - 所有的服务员都走了
                                //将整个栈清空。
                                casHead(s, null);   // pop fulfill node
                                s = null;           // use new node next time
                                //回到外层大的 自旋中，再重新选择路径执行，此时有可能 插入一个节点。
                                break;              // restart main loop
                            }

                            //什么时候会执行到这里呢？
                            //fulfilling 匹配节点不为null，进行真正的匹配工作。

                            //获取 匹配节点的 下一个节点。
                            SNode mn = m.next;
                            //尝试匹配，匹配成功，则将fulfilling 和 m 一起出栈。
                            if (m.tryMatch(s)) {
                                //结对出栈
                                casHead(s, mn);     // pop both s and m

                                return (E) ((mode == REQUEST) ?
                                        m.item : //当前NODE模式为REQUEST类型：返回匹配节点的m.item 数据域
                                        s.item);//当前NODE模式为DATA类型：返回Node.item 数据域，当前请求提交的 数据e
                            } else {
                                // lost match
                                //强制出栈
                                s.casNext(m, mn);   // help unlink
                            }
                        }
                    }
                }

                /**
                 * CASE3：什么时候会执行？
                 * 栈顶模式为 FULFILLING模式:表示栈顶和栈顶下面的栈帧正在发生匹配...
                 * 当前请求需要做 协助 工作。
                 */
                else {                            // help a fulfiller
                    //h 表示的是 fulfilling节点,m fulfilling匹配的节点。
                    SNode m = h.next;               // m is h's match
                    //m == null 什么时候可能成立呢？
                    //当s.next节点 超时或者被外部线程中断唤醒后，会执行 clean 操作 将 自己清理出栈，此时
                    //站在匹配者线程 来看，真有可能拿到一个null。
                    if (m == null) {
                        // waiter is gone
                        //清空栈
                        casHead(h, null);           // pop fulfilling node
                    }

                    //大部分情况：走else分支。
                    else {
                        //获取栈顶匹配节点的 下一个节点
                        SNode mn = m.next;
                        //条件成立：说明 m 和 栈顶 匹配成功
                        if (m.tryMatch(h)) {
                            // help match
                            //双双出栈，让栈顶指针指向 匹配节点的下一个节点。
                            casHead(h, mn);         // pop both h and m
                        } else {
                            // lost match
                            //强制出栈
                            h.casNext(m, mn);       // help unlink
                        }
                    }
                }
            }
        }

        /**
         * Spins/blocks until node s is matched by a fulfill operation.
         * -- 旋转/阻塞，直到节点s通过执行操作匹配。
         *
         * @param s the waiting node 当前请求Node,执行等待的节点
         * @param timed true if timed wait 当前请求是否支持 超时限制
         * @param nanos timeout value 如果请求支持超时限制，nanos 表示超时等待时长。
         * @return matched node, or s if cancelled,返回与之匹配的节点，如果取消则返回节点自己
         */
        SNode awaitFulfill(SNode s, boolean timed, long nanos) {
            /*
             * When a node/thread is about to block, it sets its waiter
             * field and then rechecks state at least one more time
             * before actually parking, thus covering race vs
             * fulfiller noticing that waiter is non-null so should be
             * woken.
             *
             * When invoked by nodes that appear at the point of call
             * to be at the head of the stack, calls to park are
             * preceded by spins to avoid blocking when producers and
             * consumers are arriving very close in time.  This can
             * happen enough to bother only on multiprocessors.
             *
             * The order of checks for returning out of main loop
             * reflects fact that interrupts have precedence over
             * normal returns, which have precedence over
             * timeouts. (So, on timeout, one last check for match is
             * done before giving up.) Except that calls from untimed
             * SynchronousQueue.{poll/offer} don't check interrupts
             * and don't wait at all, so are trapped in transfer
             * method rather than calling awaitFulfill.
             */

            //等待的截止时间。
            final long deadline = timed ?
                    System.nanoTime() + nanos : // timed == true  =>  System.nanoTime() + nanos
                    0L;

            //获取当前请求线程..
            Thread w = Thread.currentThread();

            /**
             * spins 表示当前请求线程 在 下面的 for(;;) 自旋检查中，自旋次数。
             * 如果达到spins自旋次数时，当前线程对应的Node 仍然未被匹配成功，
             * 那么再选择 挂起 当前请求线程。
             *
             * 前提是：我们任务自旋要比挂起性能更高，所以选择先自旋再挂起
             */
            int spins = (

                    shouldSpin(s) ?//是否可以自旋？

                            //timed == true 指定了超时限制的，这个时候采用 maxTimedSpins == 32 ,
                            // 否则采用 32 * 16 = 512
                            (timed ? maxTimedSpins : maxUntimedSpins) :

                            0 //如果不能自旋，则自旋次数=0
            );

            //自旋检查逻辑：1.是否匹配  2.是否超时  3.是否被中断..
            for (; ; ) {

                if (w.isInterrupted()) {
                    //条件成立：说明当前线程收到中断信号，需要设置Node状态为 取消状态。

                    //Node对象的 match 指向 当前Node 说明该Node状态就是 取消状态。
                    s.tryCancel();
                    //取消之后继续往下执行
                }

                /**
                 * m 表示与当前Node匹配的节点。
                 * 1.正常情况：有一个请求 与 当前Node 匹配成功，这个时候 s.match 指向 匹配节点。
                 * 2.取消情况：当前match 指向 当前Node...
                 */
                SNode m = s.match;
                if (m != null) {
                    //可能正常 也可能是 取消...
                    //这是该方法的唯一出口！！！！
                    return m;
                }

                //下面是线程没有中断，并且没有匹配成功也没有取消的情况.s.match == null

                if (timed) {
                    //条件成立：说明指定了超时限制..

                    //nanos 表示距离超时 还有多少纳秒..
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        //条件成立：说明已经超时了或者刚好到超时时间

                        //超时的时候也会置当前Node状态为 取消状态.. match-->当前Node
                        //TODO:中断的时候也是取消，超时的时候也是取消，那么如果区分这两种不同的状态呢？
                        s.tryCancel();
                        continue;
                    }
                }

                //到这里：没有设置超时，或者还没有超时，继续自旋

                if (spins > 0) {
                    //条件成立：说明当前线程还可以进行自旋检查...

                    spins = shouldSpin(s) ? //TODO 为什么这里又判断一次呢？

                            (spins - 1) ://自旋次数 累积 递减，表示下一次自旋开始了

                            0;
                }

                //执行到这:说明执行次数用完了，spins == 0 ，已经不允许再进行自旋检查了
                else if (s.waiter == null) {
                    /**
                     * 执行次数用完了，可能还没有匹配到，接下来可能需要挂起了，
                     * 所以把当前Node对应的Thread 保存到 Node.waiter字段中..
                     */
                    s.waiter = w; // establish waiter so can park next iter
                }

                /**
                 * 执行到这里，说明执行次数用完了，并且s.waiter也又值了，下面就可以进行挂起相关的操作了
                 * 下面2个情况就是看是否指定了超时，分别采用不同的挂起策略
                 */

                else if (!timed) {
                    /**
                     * 前提：
                     * 1.自旋次数用完
                     * 2.s.waiter已经赋值了
                     *
                     * 接下来就要挂起了
                     *
                     * 此时如果没有指定超时：说明当前Node对应的请求  未指定超时限制。
                     */

                    //使用不指定超时限制的park方法 挂起当前线程，直到 当前线程被外部线程 使用unpark唤醒。
                    LockSupport.park(this);
                    //唤醒之后继续从这里开始执行，一般在下一次for循环的时候就会退出
                } else if (nanos > spinForTimeoutThreshold) {
                    /**
                     * 前提：
                     * 1.自旋次数用完
                     * 2.s.waiter已经赋值了
                     * 3.指定了超时，并且指定了超时时间,并且超时时间大于1000ns
                     *
                     * 接下来就要挂起了
                     */
                    //什么时候执行到这里？ timed == true 设置了 超时限制..

                    //条件成立：nanos > 1000 纳秒的值，只有这种情况下，才允许挂起当前线程..否则 说明 超时给的太少了...挂起和唤醒的成本 远大于 空转自旋...
                    LockSupport.parkNanos(this, nanos);
                    //唤醒之后继续从这里开始执行，一般在下一次for循环的时候就会退出
                }
            }
        }

        /**
         * Returns true if node s is at head or there is an active
         * fulfiller.
         * -- 如果节点s在头或有一个活跃的履行者，则返回true。
         *
         * 判断参数节点s是否可以自旋
         *
         * @param s 当前等待被匹配的节点
         */
        boolean shouldSpin(SNode s) {
            //获取栈顶
            SNode h = head;

            return (h == s //条件一 h == s ：条件成立 说明当前s 就是栈顶，当前请求刚入栈，允许自旋检查...

                    /**
                     * 条件二 h == null : 什么时候成立？
                     * 前提：当前节点s不是头节点
                     *
                     * 当前s节点 自旋检查期间，又来了一个 与当前s 节点匹配的请求，双双出栈了...条件会成立。
                     *
                     * ｜ F  ｜ 新来了REQUEST类型的请求，入栈之后类型为FULFILLING，正好与之前栈顶s匹配
                     * ｜————｜
                     * ｜ D  ｜ s为DATA类型的节点，正在自旋
                     * ｜————｜
                     *
                     * 此时这2个元素是一起出栈的
                     * TODO 为什么还要自旋呢？
                     *
                     * 是不是当前场景是这样的，当前头节点类型为F，与下面的D节点匹配成功之后出栈了，
                     * 在某一个瞬间h为null,此时s节点肯定是可以自旋等待的
                     * ｜ F  ｜
                     * ｜————｜
                     * ｜ D  ｜
                     * ｜————｜
                     * ｜ s  ｜
                     * ｜————｜
                     */
                    || h == null

                    /**
                     * 条件三 isFulfilling(h.mode) ：
                     * 前提：1.当前 s 不是 栈顶元素
                     *      2.并且栈顶元素h不为null
                     *
                     * 如果当前栈顶正在匹配中，这种状态 栈顶下面的元素，都允许自旋检查。
                     */
                    || isFulfilling(h.mode));
        }

        /**
         * Unlinks s from the stack.
         */
        void clean(SNode s) {
            //清空数据域
            s.item = null;   // forget item
            //释放线程引用..
            s.waiter = null; // forget thread
            /*
             * At worst we may need to traverse entire stack to unlink
             * s. If there are multiple concurrent calls to clean, we
             * might not see s if another thread has already removed
             * it. But we can stop when we see any node known to
             * follow s. We use s.next unless it too is cancelled, in
             * which case we try the node one past. We don't check any
             * further because we don't want to doubly traverse just to
             * find sentinel.
             * -- 最糟糕的是，我们可能需要遍历整个堆栈以取消s的链接。如果有多个并发调用要清理，则如果另一个线程已将其删除，则可能看不到。
             * 但是当看到任何已知跟随s的节点时，我们可以停止。除非也将其取消，否则我们将使用s.next，在这种情况下，我们将尝试过去一个节点。我们不做进一步检查，因为我们不想为了找到标记而进行双重遍历。
             */

            //检查取消节点的截止位置
            SNode past = s.next;

            if (past != null && past.isCancelled()) {
                past = past.next;
            }

            // Absorb cancelled nodes at head -- 吸收头部的取消节点

            //当前循环检查节点
            SNode p;
            //从栈顶开始向下检查，将栈顶开始向下连续的 取消状态的节点 全部清理出去，直到碰到past为止。
            while ((p = head) != null && p != past && p.isCancelled()) {
                casHead(p, p.next);
            }

            // Unsplice embedded nodes
            while (p != null && p != past) {
                //获取p.next
                SNode n = p.next;
                if (n != null && n.isCancelled()) {
                    p.casNext(n, n.next);
                } else {
                    p = n;
                }
            }
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;

        /**
         * 该栈顶引用的内层偏移量
         */
        private static final long headOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = TransferStack.class;
                headOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("head"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /** Dual Queue */
    /**
     * 双端队列
     * 公平模式同步队列
     */
    static final class TransferQueue<E> extends Transferer<E> {
        /*
         * This extends Scherer-Scott dual queue algorithm, differing,
         * among other ways, by using modes within nodes rather than
         * marked pointers. The algorithm is a little simpler than
         * that for stacks because fulfillers do not need explicit
         * nodes, and matching is done by CAS'ing QNode.item field
         * from non-null to null (for put) or vice versa (for take).
         *
         * -- 这扩展了Scherer-Scott双队列算法，其不同之处在于，通过使用节点内的模式而不是标记的指针来实现。
         * 该算法比堆栈的算法更简单，因为实现者不需要显式节点，并且匹配是通过CAS的QNode.item字段从非null到null（用于放置）或反之亦然（用于获取）来完成的。
         */

        /**
         * Node class for TransferQueue.
         */
        static final class QNode {

            //指向当前节点的下一个节点，组装链表使用的。
            volatile QNode next;          // next node in queue

            //数据域  Node代表的是DATA类型，item表示数据   否则 Node代表的REQUEST类型，item == null

            volatile Object item;         // CAS'ed to or from null

            //当Node对应的线程 未匹配到节点时，对应的线程 最终会挂起，挂起之前会保留 线程引用到waiter ，
            //方法 其它Node匹配当前节点时 唤醒 当前线程..
            volatile Thread waiter;       // to control park/unpark

            //true 当前Node是一个DATA类型   false表示当前Node是一个REQUEST类型。
            final boolean isData;

            QNode(Object item, boolean isData) {
                this.item = item;
                this.isData = isData;
            }

            //修改当前节点next引用
            boolean casNext(QNode cmp, QNode val) {
                return next == cmp &&
                        UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            //修改当前节点数据域item
            boolean casItem(Object cmp, Object val) {
                return item == cmp &&
                        UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
            }

            /**
             * Tries to cancel by CAS'ing ref to this as item.
             * 尝试取消当前node
             * 取消状态的Node，它的item域，指向自己Node。
             */
            void tryCancel(Object cmp) {
                UNSAFE.compareAndSwapObject(this, itemOffset, cmp, this);
            }

            //判断当前Node是否为取消状态
            boolean isCancelled() {
                return item == this;
            }

            /**
             * Returns true if this node is known to be off the queue
             * because its next pointer has been forgotten due to
             * an advanceHead operation.
             *
             * 判断当前节点是否 “不在” 队列内，当next指向自己时，说明节点已经出队。
             */
            boolean isOffList() {
                return next == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;

            private static final long itemOffset;

            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = QNode.class;
                    itemOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("item"));
                    nextOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }

        /**
         *  这是一个非常典型的 queue , 它有如下的特点
         *  1. 整个队列有 head, tail 两个节点
         *  2. 队列初始化时会有个 dummy 节点
         *  3. 这个队列的头节点是个 dummy 节点/ 或 哨兵节点, 所以操作的总是队列中的第二个节点(AQS的设计中也是这也)
         */

        /**
         * Head of queue
         */
        //指向队列的dummy节点
        transient volatile QNode head;

        /**
         * Tail of queue
         */
        //指向队列的尾节点。
        transient volatile QNode tail;

        /**
         * Reference to a cancelled node that might not yet have been
         * unlinked from queue because it was the last inserted node
         * when it was cancelled.
         * 表示被清理节点的前驱节点。因为入队操作是 两步完成的，
         * 第一步：t.next = newNode
         * 第二步：tail = newNode
         * 所以，队尾节点出队，是一种非常特殊的情况，需要特殊处理，回头讲！
         *
         *
         * 对应 中断或超时的 前继节点,这个节点存在的意义是标记, 它的下个节点要删除
         * 何时使用:
         * 当你要删除 节点 node, 若节点 node 是队列的末尾, 则开始用这个节点,
         * 为什么呢？
         * 大家知道 删除一个节点 直接 A.CASNext(B, B.next) 就可以,但是当  节点 B 是整个队列中的末尾元素时,
         * 一个线程删除节点B, 一个线程在节点B之后插入节点 这样操作容易致使插入的节点丢失, 这个cleanMe很像
         * ConcurrentSkipListMap 中的 删除添加的 marker 节点, 他们都是起着相同的作用
         */
        transient volatile QNode cleanMe;

        TransferQueue() {
            /**
             * 构造一个 dummy node, 而整个 queue 中永远会存在这样一个 dummy node
             * dummy node 的存在使得 代码中不存在复杂的 if 条件判断
             */
            QNode h = new QNode(null, false); // initialize to dummy node.-- 初始化为虚拟节点
            head = h;
            tail = h;
        }

        /**
         * Tries to cas nh as new head; if successful, unlink
         * old head's next node to avoid garbage retention.
         * 设置头指针指向新的节点，蕴含操作：老的头节点出队。
         */
        void advanceHead(QNode h, QNode nh) {
            if (h == head && UNSAFE.compareAndSwapObject(this, headOffset, h, nh)) {
                //this.next = this 就表示出队了
                //推进 head 节点,将 老节点的 oldNode.next = this, help gc
                h.next = h; // forget old next
            }
        }

        /**
         * Tries to cas nt as new tail.
         * 更新队尾节点 为新的队尾。
         *
         * @param t 老的队尾
         * @param nt 新的队尾
         */
        void advanceTail(QNode t, QNode nt) {
            if (tail == t) {
                UNSAFE.compareAndSwapObject(this, tailOffset, t, nt);
            }
        }

        /**
         * Tries to CAS cleanMe slot.
         */
        boolean casCleanMe(QNode cmp, QNode val) {
            return cleanMe == cmp &&
                    UNSAFE.compareAndSwapObject(this, cleanMeOffset, cmp, val);
        }

        /**
         * Puts or takes an item.
         *
         * 若队列为空 / 队列中的尾节点和自己的 类型相同, 则添加 node
         * 到队列中, 直到 timeout/interrupt/其他线程和这个线程匹配
         * timeout/interrupt awaitFulfill方法返回的是 node 本身
         * 匹配成功的话, 要么返回 null (producer返回的), 或正真的传递值 (consumer 返回的)
         *
         * 队列不为空, 且队列的 head.next 节点是当前节点匹配的节点,
         * 进行数据的传递匹配, 并且通过 advanceHead 方法帮助 先前 block 的节点 dequeue
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            /* Basic algorithm is to loop trying to take either of
             * two actions:
             *
             * 1. If queue apparently empty or holding same-mode nodes,
             *    try to add node to queue of waiters, wait to be
             *    fulfilled (or cancelled) and return matching item.
             *
             * 2. If queue apparently contains waiting items, and this
             *    call is of complementary mode, try to fulfill by CAS'ing
             *    item field of waiting node and dequeuing it, and then
             *    returning matching item.
             *
             * In each case, along the way, check for and try to help
             * advance head and tail on behalf of other stalled/slow
             * threads.
             *
             * The loop starts off with a null check guarding against
             * seeing uninitialized head or tail values. This never
             * happens in current SynchronousQueue, but could if
             * callers held non-volatile/final ref to the
             * transferer. The check is here anyway because it places
             * null checks at top of loop, which is usually faster
             * than having them implicitly interspersed.

             *
             * 这个 producer / consumer 的主方法, 主要分为两种情况
             *
             * 1. 若队列为空 / 队列中的尾节点和自己的 类型相同, 则添加 node
             *      到队列中, 直到 timeout/interrupt/其他线程和这个线程匹配
             *      timeout/interrupt awaitFulfill方法返回的是 node 本身
             *      匹配成功的话, 要么返回 null (producer返回的), 或正真的传递值 (consumer 返回的)
             *
             * 2. 队列不为空, 且队列的 head.next 节点是当前节点匹配的节点,
             *      进行数据的传递匹配, 并且通过 advanceHead 方法帮助 先前 block 的节点 dequeue
             *
             */
            //s 指向当前请求 对应Node
            QNode s = null; // constructed/reused as needed 根据需要新建或者复用

            /**
             * 1.判断 e != null 用于区分 producer 与 consumer
             * isData == true 表示 当前请求是一个写数据操作（DATA）   否则isData == false 表示当前请求是一个 REQUEST操作。
             */
            boolean isData = (e != null);

            //自旋..
            for (; ; ) {
                QNode t = tail;
                QNode h = head;
                if (t == null || h == null) {               // 2. 数据未初始化, continue 重来,看到未初始化的值 说明还在初始化中,构造方法还没有执行完成
                    continue;                       // saw uninitialized value,spin
                }

                //条件一：成立，说明head和tail同时指向dummy节点，当前队列实际情况 就是 空队列。此时当前请求需要做入队操作，因为没有任何节点 可以去匹配。
                //条件二：队列不是空，队尾节点与当前请求类型是一致的情况。说明也是无法完成匹配操作的情况，此时当前节点只能入队...
                if (h == t || t.isData == isData) {         //3. 队列为空, 或队列尾节点和自己相同 (注意这里是和尾节点比价, 下面进行匹配时是和 head.next 进行比较)
                    QNode tn = t.next;
                    /**
                     * 获取当前队尾t 的next节点 tn - t.next
                     * 如果当前线程执行到这里的时候，在此之前并没有其他线程修改t的next引用，则tail不会发生变化
                     * 但是此方法是支持多线程访问的，所以要考虑tail已经发生了改变的情况
                     *
                     * 因为多线程环境，当前线程在入队之前，其它线程有可能已经入队过了..改变了 tail 引用。
                     */
                    if (t != tail) {                        // 4. tail 改变了, 重新再来
                        // inconsistent read：读不一致
                        //线程回到自旋...再选择路径执行。
                        continue;
                    }

                    /**
                     * 获取当前队尾t 的next节点 tn - t.next
                     * 如果当前线程执行到这里的时候，在此之前并没有其他线程修改t的next引用，则tail不会发生变化
                     * 但是此方法是支持多线程访问的，所以要考虑tail已经发生了改变的情况
                     *
                     * 因为多线程环境，当前线程在入队之前，其它线程有可能已经入队过了，已经设置了t.next的值，但是还没来得及设置 tail = newNode
                     *
                     * 说明已经有线程 入队了，且只完成了 入队的 第一步：设置t.next = newNode， 第二步可能尚未完成..
                     */
                    if (tn != null) {                       // 5. 其他线程添加了 tail.next, 所以帮助推进 tail，lagging tail(尾巴落后了，tn才是正在的tail)
                        //条件成立：说明已经有线程 入队了，且只完成了 入队的 第一步：设置t.next = newNode， 第二步可能尚未完成..

                        /**
                         * 协助更新tail 指向新的　尾结点。
                         * 只是协助，不保证成功，原因还是因为存在并发
                         */
                        advanceTail(t, tn);
                        //线程回到自旋...再选择路径执行。
                        continue;
                    }

                    if (timed && nanos <= 0) {               // 6. 调用的方法的 wait 类型的, 并且 超时了, 直接返回 null, 直接见 SynchronousQueue.poll() 方法,说明此 poll 的调用只有当前队列中正好有一个与之匹配的线程在等待被【匹配才有返回值
                        //条件成立：说明当前调用transfer方法的 上层方法 可能是 offer() 无参的这种方法进来的，这种方法不支持 阻塞等待...

                        // can't wait
                        //检查未匹配到,直接返回null。
                        return null;
                    }

                    //条件成立：说明当前请求尚未 创建对应的node
                    if (s == null) {
                        //创建node过程...
                        s = new QNode(e, isData);           // 7. 构建节点 QNode
                    }

                    //条件 不成立：!t.casNext(null, s)  说明当前t仍然是tail，当前线程对应的Node入队的第一步 完成！
                    if (!t.casNext(null, s)) {        // 8. 将 新建的节点加入到 队列中
                        // failed to link in
                        continue;
                    }

                    //更新队尾 为咱们请求节点。
                    advanceTail(t, s);              // swing tail and wait

                    //当前节点 等待匹配....
                    //当前请求为DATA模式时：e 请求带来的数据
                    //x == this 当前SNode对应的线程 取消状态
                    //x == null 表示已经有匹配节点了，并且匹配节点拿走了item数据。

                    //当前请求为REQUEST模式时：e == null
                    //x == this 当前SNode对应的线程 取消状态
                    //x != null 且 item != this  表示当前REQUEST类型的Node已经匹配到一个DATA类型的Node了。
                    Object x = awaitFulfill(s, e, timed, nanos);    // 10. 调用awaitFulfill, 若节点是 head.next, 则进行一些自旋, 若不是的话, 直接 block, 直到有其他线程 与之匹配, 或它自己进行线程的中断

                    if (x == s) {                   // wait was cancelled，11. 若 (x == s)节点s 对应额线程 wait 超时 或线程中断, 不然的话 x == null (s 是 producer) 或 是正真的传递值(s 是 consumer)
                        //说明当前Node状态为 取消状态，需要做 出队逻辑。

                        //清理出队逻辑，最后讲。
                        clean(t, s);                // 12. 对节点 s 进行清除, 若 s 不是链表的最后一个节点, 则直接 CAS 进行 节点的删除, 若 s 是链表的最后一个节点, 则 要么清除以前的 cleamMe 节点(cleamMe != null), 然后将 s.prev 设置为 cleanMe 节点, 下次进行删除 或直接将 s.prev 设置为cleanMe
                        return null;
                    }

                    //执行到这里说明 当前Node 匹配成功了...
                    //1.当前线程在awaitFulfill方法内，已经挂起了...此时运行到这里时是被 匹配节点的线程使用LockSupport.unpark() 唤醒的..
                    //被唤醒：当前请求对应的节点，肯定已经出队了，因为匹配者线程 是先让当前Node出队的，再唤醒当前Node对应线程的。

                    //2.当前线程在awaitFulfill方法内，处于自旋状态...此时匹配节点 匹配后，它检查发现了，然后返回到上层transfer方法的。
                    //自旋状态返回时：当前请求对应的节点，不一定就出队了...

                    //被唤醒时：s.isOffList() 条件会成立。  !s.isOffList() 不会成立。
                    //条件成立：说明当前Node仍然在队列内，需要做 匹配成功后 出队逻辑。
                    if (!s.isOffList()) {           // not already unlinked
                        //其实这里面做的事情，就是防止当前Node是自旋检查状态时发现 被匹配了，然后当前线程 需要将
                        //当前线程对应的Node做出队逻辑.

                        //t 当前s节点的前驱节点，更新dummy节点为 s节点。表示head.next节点已经出队了...
                        advanceHead(t, s);          // unlink if head

                        //x != null 且 item != this  表示当前REQUEST类型的Node已经匹配到一个DATA类型的Node了。
                        //因为s节点已经出队了，所以需要把它的item域 给设置为它自己，表示它是个取消出队状态。
                        if (x != null)              // and forget fields
                        {
                            s.item = s;
                        }
                        //因为s已经出队，所以waiter一定要保证是null。
                        s.waiter = null;
                    }

                    //x != null 成立，说明当前请求是REQUEST类型，返回匹配到的数据x
                    //x != null 不成立，说明当前请求是DATA类型，返回DATA请求时的e。
                    return (x != null) ? (E) x : e;
                }

                //CASE2：队尾节点 与 当前请求节点 互补 （队尾->DATA，请求类型->REQUEST）  (队尾->REQUEST, 请求类型->DATA)
                else {                            // complementary-mode
                    //h.next节点 其实是真正的队头，请求节点 与队尾模式不同，需要与队头 发生匹配。因为TransferQueue是一个 公平模式
                    QNode m = h.next;               // node to fulfill

                    //条件一：t != tail 什么时候成立呢？ 肯定是并发导致的，其它线程已经修改过tail了，有其它线程入队过了..当前线程看到的是过期数据，需要重新循环
                    //条件二：m == null 什么时候成立呢？ 肯定是其它请求先当前请求一步，匹配走了head.next节点。
                    //条件三：条件成立，说明已经有其它请求匹配走head.next了。。。当前线程看到的是过期数据。。。重新循环...
                    if (t != tail || m == null || h != head) {
                        continue;                   // inconsistent read
                    }

                    //执行到这里，说明t m h 不是过期数据,是准确数据。目前来看是准确的！
                    //获取匹配节点的数据域 保存到x
                    Object x = m.item;

                    //条件一：isData == (x != null)
                    //isData 表示当前请求是什么类型  isData == true：当前请求是DATA类型  isData == false：当前请求是REQUEST类型。
                    //1.假设isData == true   DATA类型
                    //m其实表示的是 REQUEST 类型的NODE，它的数据域是 null  => x==null
                    //true == (null != null)  => true == false => false

                    //2.假设isData == false REQUEST类型
                    //m其实表示的是 DATA 类型的NODE，它的数据域是 提交是的e ，并且e != null。
                    //false == (obj != null) => false == true => false

                    //总结：正常情况下，条件一不会成立。

                    //条件二：条件成立，说明m节点已经是 取消状态了...不能完成匹配，当前请求需要continue，再重新选择路径执行了..

                    //条件三：!m.casItem(x, e)，前提条件 m 非取消状态。
                    //1.假设当前请求为REQUEST类型   e == null
                    //m 是 DATA类型了...
                    //相当于将匹配的DATA Node的数据域清空了，相当于REQUEST 拿走了 它的数据。

                    //2.假设当前请求为DATA类型    e != null
                    //m 是 REQUEST类型了...
                    //相当于将匹配的REQUEST Node的数据域 填充了，填充了 当前DATA 的 数据。相当于传递给REQUEST请求数据了...
                    if (isData == (x != null) ||    // m already fulfilled
                            x == m ||                   // m cancelled
                            !m.casItem(x, e)) {         // lost CAS
                        advanceHead(h, m);          // dequeue and retry
                        continue;
                    }

                    //执行到这里，说明匹配已经完成了，匹配完成后，需要做什么？
                    //1.将真正的头节点 出队。让这个真正的头结点成为dummy节点
                    advanceHead(h, m);              // successfully fulfilled
                    //2.唤醒匹配节点的线程..
                    LockSupport.unpark(m.waiter);

                    //x != null 成立，说明当前请求是REQUEST类型，返回匹配到的数据x
                    //x != null 不成立，说明当前请求是DATA类型，返回DATA请求时的e。
                    return (x != null) ? (E) x : e;
                }
            }
        }

        /**
         * Spins/blocks until node s is fulfilled.
         *
         * @param s the waiting node
         * @param e the comparison value for checking match
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched item, or s if cancelled
         */
        Object awaitFulfill(QNode s, E e, boolean timed, long nanos) {
            /* Same idea as TransferStack.awaitFulfill */
            //deadline 表示等待截止时间...
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            //当前请求节点的线程..
            Thread w = Thread.currentThread();
            //允许自旋检查的次数..
            int spins = ((head.next == s) ?
                    (timed ? maxTimedSpins : maxUntimedSpins) : 0);

            //自旋：1.检查状态等待匹配  2.挂起线程  3.检查状态 是否被中断 或者 超时..
            for (; ; ) {
                //条件成立：说明线程等待过程中，收到了中断信号，属于中断唤醒..
                if (w.isInterrupted()) {
                    //更新线程对应的Node状态为 取消状态..
                    //数据域item 指向当前Node自身，表示取消状态.

                    s.tryCancel(e);
                }

                //获取当前Node数据域
                Object x = s.item;
                //item有几种情况呢？
                //当SNode模式为DATA模式时：
                //1.item != null 且 item != this  表示请求要传递的数据 put(E e)
                //2.item == this 当前SNode对应的线程 取消状态
                //3.item == null 表示已经有匹配节点了，并且匹配节点拿走了item数据。

                //当SNode模式为REQUEST模式时：
                //1.item == null 时，正常状态，当前请求仍然未匹配到对应的DATA请求。
                //2.item == this 当前SNode对应的线程 取消状态
                //3.item != null 且 item != this  表示当前REQUEST类型的Node已经匹配到一个DATA类型的Node了。

                //条件成立：
                //当前请求为DATA模式时：e 请求带来的数据
                //item == this 当前SNode对应的线程 取消状态
                //item == null 表示已经有匹配节点了，并且匹配节点拿走了item数据。

                //当前请求为REQUEST模式时：e == null
                //item == this 当前SNode对应的线程 取消状态
                //item != null 且 item != this  表示当前REQUEST类型的Node已经匹配到一个DATA类型的Node了。
                if (x != e) {
                    return x;
                }

                //条件成立：说明请求指定了超时限制..
                if (timed) {
                    //nanos表示距离截止时间的长度..
                    nanos = deadline - System.nanoTime();
                    //条件成立：说明当前Node对应的线程 已经等待超时了，需要取消了.
                    if (nanos <= 0L) {
                        s.tryCancel(e);
                        continue;
                    }
                }

                //条件成立：说明当前线程 还可以进行自旋检查..
                if (spins > 0) {
                    //递减..

                    --spins;
                }

                //执行到这里，说明spins == 0;
                //条件成立：当前Node尚未设置waiter字段..
                else if (s.waiter == null) {
                    //保存当前Node对应的线程，方便后面挂起线程后，外部线程使用s.waiter字段唤醒 当前Node对应的线程。

                    s.waiter = w;
                }

                //条件成立：说明当前请求未指定超时限制。挂起采用 不指定超时的挂起方法..
                else if (!timed) {
                    LockSupport.park(this);
                }

                //执行到这里，说明 timed==true
                //条件 不成立：nanos 太小了，没有必要挂起线程了，还不如自旋 实在。
                else if (nanos > spinForTimeoutThreshold) {
                    //nanos > 1000.

                    LockSupport.parkNanos(this, nanos);
                }
            }
        }

        /**
         * Gets rid of cancelled node s with original predecessor pred.
         * -- 去除具有原始前任pred的已取消节点s。
         */
        void clean(QNode pred, QNode s) {
            s.waiter = null; // forget thread
            /*
             * At any given time, exactly one node on list cannot be
             * deleted -- the last inserted node. To accommodate this,
             * if we cannot delete s, we save its predecessor as
             * "cleanMe", deleting the previously saved version
             * first. At least one of node s or the node previously
             * saved can always be deleted, so this always terminates.
             */
            while (pred.next == s) { // Return early if already unlinked
                QNode h = head;
                QNode hn = h.next;   // Absorb cancelled first node as head
                if (hn != null && hn.isCancelled()) {
                    advanceHead(h, hn);
                    continue;
                }
                QNode t = tail;      // Ensure consistent read for tail
                if (t == h) {
                    return;
                }
                QNode tn = t.next;
                if (t != tail) {
                    continue;
                }
                if (tn != null) {
                    advanceTail(t, tn);
                    continue;
                }
                if (s != t) {        // If not tail, try to unsplice
                    QNode sn = s.next;
                    if (sn == s || pred.casNext(s, sn)) {
                        return;
                    }
                }

                QNode dp = cleanMe;
                if (dp != null) {    // Try unlinking previous cancelled node
                    QNode d = dp.next;
                    QNode dn;
                    if (d == null ||               // d is gone or
                            d == dp ||                 // d is off list or
                            !d.isCancelled() ||        // d not cancelled or
                            (d != t &&                 // d not tail and
                                    (dn = d.next) != null &&  //   has successor
                                    dn != d &&                //   that is on list
                                    dp.casNext(d, dn)))       // d unspliced
                    {
                        casCleanMe(dp, null);
                    }
                    if (dp == pred) {
                        return;      // s is already saved node
                    }
                } else if (casCleanMe(null, pred)) {
                    return;          // Postpone cleaning s
                }
            }
        }

        private static final sun.misc.Unsafe UNSAFE;

        private static final long headOffset;

        private static final long tailOffset;

        private static final long cleanMeOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = TransferQueue.class;
                headOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("head"));
                tailOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("tail"));
                cleanMeOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("cleanMe"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * The transferer. Set only in constructor, but cannot be declared
     * as final without further complicating serialization.  Since
     * this is accessed only at most once per public method, there
     * isn't a noticeable performance penalty for using volatile
     * instead of final here.
     */
    private transient volatile Transferer<E> transferer;

    /**
     * Creates a {@code SynchronousQueue} with nonfair access policy.
     */
    public SynchronousQueue() {
        this(false);
    }

    /**
     * Creates a {@code SynchronousQueue} with the specified fairness policy.
     *
     * @param fair if true, waiting threads contend in FIFO order for
     * access; otherwise the order is unspecified.
     */
    public SynchronousQueue(boolean fair) {
        transferer = fair ?
                new TransferQueue<E>() ://公平
                new TransferStack<E>();//非公平
    }

    /**
     * Adds the specified element to this queue, waiting if necessary for
     * another thread to receive it.
     * -- 将指定的元素添加到此队列，如果有必要，等待另一个线程接收它。
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        if (transferer.transfer(e, false, 0) == null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    /**
     * Inserts the specified element into this queue, waiting if necessary
     * up to the specified wait time for another thread to receive it.
     * -- 将指定的元素插入此队列，如有必要，请等待指定的等待时间，以便另一个线程接收它。
     *
     * @return {@code true} if successful, or {@code false} if the specified waiting time elapses before a consumer appears -- @code true}（如果成功），或{@code false}（如果在出现消费者之前经过了指定的等待时间）
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        if (transferer.transfer(e, true, unit.toNanos(timeout)) != null) {
            return true;
        }
        if (!Thread.interrupted()) {
            return false;
        }
        throw new InterruptedException();
    }

    /**
     * Inserts the specified element into this queue, if another thread is
     * waiting to receive it.
     *
     * @param e the element to add
     * @return {@code true} if the element was added to this queue, else
     * {@code false}
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        return transferer.transfer(e, true, 0) != null;
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * for another thread to insert it.
     *
     * @return the head of this queue
     * @throws InterruptedException {@inheritDoc}
     */
    public E take() throws InterruptedException {
        E e = transferer.transfer(null, false, 0);
        if (e != null) {
            return e;
        }
        Thread.interrupted();
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, waiting
     * if necessary up to the specified wait time, for another thread
     * to insert it.-- 检索并删除此队列的头，如有必要，可等待指定的等待时间，直到另一个线程将其插入。
     *
     * @return the head of this queue, or {@code null} if the
     * specified waiting time elapses before an element is present
     * @throws InterruptedException {@inheritDoc}
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = transferer.transfer(null, true, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted()) {
            return e;
        }
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, if another thread
     * is currently making an element available.
     *
     * @return the head of this queue, or {@code null} if no
     * element is available
     */
    public E poll() {
        return transferer.transfer(null, true, 0);
    }

    /**
     * Always returns {@code true}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return {@code true}
     */
    public boolean isEmpty() {
        return true;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     */
    public int size() {
        return 0;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     */
    public int remainingCapacity() {
        return 0;
    }

    /**
     * Does nothing.
     * A {@code SynchronousQueue} has no internal capacity.
     */
    public void clear() {
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element
     * @return {@code false}
     */
    public boolean contains(Object o) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element to remove
     * @return {@code false}
     */
    public boolean remove(Object o) {
        return false;
    }

    /**
     * Returns {@code false} unless the given collection is empty.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false} unless given collection is empty
     */
    public boolean containsAll(Collection<?> c) {
        return c.isEmpty();
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     */
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     */
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code null}.
     * A {@code SynchronousQueue} does not return elements
     * unless actively waited on.
     *
     * @return {@code null}
     */
    public E peek() {
        return null;
    }

    /**
     * Returns an empty iterator in which {@code hasNext} always returns
     * {@code false}.
     *
     * @return an empty iterator
     */
    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }

    /**
     * Returns an empty spliterator in which calls to
     * {@link Spliterator#trySplit()} always return {@code null}.
     *
     * @return an empty spliterator
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return Spliterators.emptySpliterator();
    }

    /**
     * Returns a zero-length array.
     *
     * @return a zero-length array
     */
    public Object[] toArray() {
        return new Object[0];
    }

    /**
     * Sets the zeroeth element of the specified array to {@code null}
     * (if the array has non-zero length) and returns it.
     *
     * @param a the array
     * @return the specified array
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
        if (a.length > 0) {
            a[0] = null;
        }
        return a;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        int n = 0;
        for (E e; (e = poll()) != null; ) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        int n = 0;
        for (E e; n < maxElements && (e = poll()) != null; ) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /*
     * To cope with serialization strategy in the 1.5 version of
     * SynchronousQueue, we declare some unused classes and fields
     * that exist solely to enable serializability across versions.
     * These fields are never used, so are initialized only if this
     * object is ever serialized or deserialized.
     *
     * -- 为了应对1.5版SynchronousQueue中的序列化策略，我们声明了一些未使用的类和字段，这些类和字段仅用于实现跨版本的可序列化性。这些字段从不使用，因此仅在对该对象进行序列化或反序列化时才进行初始化。
     */

    @SuppressWarnings("serial")
    static class WaitQueue implements java.io.Serializable {

    }

    static class LifoWaitQueue extends WaitQueue {

        private static final long serialVersionUID = -3633113410248163686L;
    }

    static class FifoWaitQueue extends WaitQueue {

        private static final long serialVersionUID = -3623113410248163686L;
    }

    private ReentrantLock qlock;

    private WaitQueue waitingProducers;

    private WaitQueue waitingConsumers;

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        boolean fair = transferer instanceof TransferQueue;
        if (fair) {
            qlock = new ReentrantLock(true);
            waitingProducers = new FifoWaitQueue();
            waitingConsumers = new FifoWaitQueue();
        } else {
            qlock = new ReentrantLock();
            waitingProducers = new LifoWaitQueue();
            waitingConsumers = new LifoWaitQueue();
        }
        s.defaultWriteObject();
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     *
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     * could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        if (waitingProducers instanceof FifoWaitQueue) {
            transferer = new TransferQueue<E>();
        } else {
            transferer = new TransferStack<E>();
        }
    }

    // Unsafe mechanics
    static long objectFieldOffset(sun.misc.Unsafe UNSAFE,
            String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }

}
