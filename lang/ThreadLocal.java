package java.lang;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This class provides thread-local variables.  These variables differ from
 * their normal counterparts in that each thread that accesses one (via its
 * {@code get} or {@code set} method) has its own, independently initialized
 * copy of the variable.
 *
 * {@code ThreadLocal} instances are typically private
 * static fields in classes that wish to associate state with a thread (e.g.,
 * a user ID or Transaction ID).
 *
 * <p>For example, the class below generates unique identifiers local to each thread.
 * -- 例如，下面的类生成每个线程本地的唯一标识符
 *
 *
 * A thread's id is assigned the first time it invokes {@code ThreadId.get()} and remains unchanged on subsequent calls.
 * -- 线程的 id 在它第一次调用 {@code ThreadId.get()} 时被分配，并且在后续调用中保持不变。
 *
 * <pre>
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // Atomic integer containing the next thread ID to be assigned
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // Thread local variable containing each thread's ID
 *     private static final ThreadLocal&lt;Integer&gt; threadId =
 *         new ThreadLocal&lt;Integer&gt;() {
 *             &#64;Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // Returns the current thread's unique ID, assigning it if necessary
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 * </pre>
 * <p>Each thread holds an implicit reference to its copy of a thread-local
 * variable as long as the thread is alive and the {@code ThreadLocal}
 * instance is accessible; after a thread goes away, all of its copies of
 * thread-local instances are subject to garbage collection (unless other
 * references to these copies exist).
 *
 * 该类提供了线程局部 (thread-local) 变量。 这些变量不同于它们的普通对应物，
 * 因为访问某个变量（通过其 get 或 set 方法）的每个线程都有自己的局部变量
 * 它独立于变量的初始化副本。ThreadLocal 实例通常是类中的 private static 字段
 * 它们希望将状态与某一个线程（例如，用户 ID 或事务 ID）相关联。
 *
 * 例如，以下类生成对每个线程唯一的局部标识符。
 *
 * 线程 ID 是在第一次调用 UniqueThreadIdGenerator.getCurrentThreadId() 时分配的，
 * 在后续调用中不会更改。
 * <pre>
 *     * import java.util.concurrent.atomic.AtomicInteger;
 *  *
 *  * public class ThreadId {
 *  *     // 原子性整数，包含下一个分配的线程Thread ID
 *  *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *  *
 *  *     // 每一个线程对应的Thread ID
 *  *     private static final ThreadLocal<Integer> threadId =
 *  *         new ThreadLocal<Integer>() {
 *  *             @Override
 *  *             protected Integer initialValue() {
 *  *                 return nextId.getAndIncrement();
 *  *         }
 *  *     };
 *  *
 *  *     // 返回当前线程对应的唯一Thread ID, 必要时会进行分配
 *  *     public static int get() {
 *  *         return threadId.get();
 *  *     }
 *  * }
 * </pre>
 * 每个线程都保持对其线程局部变量副本的隐式引用，只要线程是活动的并且 ThreadLocal 实例是可访问的
 * 在线程消失之后，其线程局部实例的所有副本都会被垃圾回收，（除非存在对这些副本的其他引用）。
 *
 * @author Josh Bloch and Doug Lea
 * @since 1.2
 */
@SuppressWarnings("all")
public class ThreadLocal<T> {

    /**
     * ThreadLocals rely on per-thread linear-probe hash maps attached
     * to each thread (Thread.threadLocals and
     * inheritableThreadLocals).  The ThreadLocal objects act as keys,
     * searched via threadLocalHashCode.  This is a custom hash code
     * (useful only within ThreadLocalMaps) that eliminates collisions
     * in the common case where consecutively constructed ThreadLocals
     * are used by the same threads, while remaining well-behaved in
     * less common cases.
     */
    //线程获取threadLocal.get()时 如果是第一次在某个 threadLocal对象上get时，会给当前线程分配一个value
    //这个value 和 当前的threadLocal对象 被包装成为一个 entry 其中 key是 threadLocal对象，value是threadLocal对象给当前线程生成的value
    //这个entry存放到 当前线程 threadLocals 这个map的哪个桶位？ 与当前 threadLocal对象的threadLocalHashCode 有关系。
    // 使用 threadLocalHashCode & (table.length - 1) 的到的位置 就是当前 entry需要存放的位置。
    private final int threadLocalHashCode = nextHashCode();

    /**
     * The next hash code to be given out. Updated atomically. Starts at
     * zero.
     *
     * 全局变量！！！！！！
     * 创建ThreadLocal对象时 会使用到，每创建一个threadLocal对象 就会使用nextHashCode 分配一个hash值给这个对象。
     */
    private static AtomicInteger nextHashCode = new AtomicInteger();

    /**
     * The difference between successively generated hash codes - turns
     * implicit sequential thread-local IDs into near-optimally spread
     * multiplicative hash values for power-of-two-sized tables.
     * 每创建一个ThreadLocal对象，这个ThreadLocal.nextHashCode 这个值就会增长 0x61c88647 。
     * 这个值 很特殊，它是 斐波那契数  也叫 黄金分割数。hash增量为 这个数字，带来的好处就是 hash分布非常均匀。
     *
     * ---- 生成hash code间隙为这个魔数，可以让生成出来的值或者说ThreadLocal的ID较为均匀地分布在2的幂大小的数组中。
     */
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * Returns the next hash code.
     * 创建新的ThreadLocal对象时  会给当前对象分配一个hash，使用这个方法。
     *
     * 可以看出，它是在上一个被构造出的ThreadLocal的ID/threadLocalHashCode的基础上加上一个魔数0x61c88647的。
     * 这个魔数的选取与斐波那契散列有关，0x61c88647对应的十进制为1640531527。
     * 斐波那契散列的乘数可以用(long) ((1L << 31) * (Math.sqrt(5) - 1))可以得到2654435769，如果把这个值给转为带符号的int，则会得到-1640531527。换句话说
     * (1L << 32) - (long) ((1L << 31) * (Math.sqrt(5) - 1))得到的结果就是1640531527也就是0x61c88647
     * 。
     * 通过理论与实践，当我们用0x61c88647作为魔数累加为每个ThreadLocal分配各自的ID也就是threadLocalHashCode再与2的幂取模，得到的结果分布很均匀。
     * ThreadLocalMap使用的是线性探测法，均匀分布的好处在于很快就能探测到下一个临近的可用slot，从而保证效率。
     *
     * @see ThreadLocal#threadLocalHashCode
     */
    private static int nextHashCode() {
        //通过 AtomicInteger cas 实现
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * Returns the current thread's "initial value" for this
     * thread-local variable.  This method will be invoked the first
     * time a thread accesses the variable with the {@link #get}
     * method, unless the thread previously invoked the {@link #set}
     * method, in which case the {@code initialValue} method will not
     * be invoked for the thread.  Normally, this method is invoked at
     * most once per thread, but it may be invoked again in case of
     * subsequent invocations of {@link #remove} followed by {@link #get}.
     *
     * <p>This implementation simply returns {@code null}; if the
     * programmer desires thread-local variables to have an initial
     * value other than {@code null}, {@code ThreadLocal} must be
     * subclassed, and this method overridden.  Typically, an
     * anonymous inner class will be used.
     *
     * @return the initial value for this thread-local
     * 默认返回null，一般情况下 咱们都是需要重写这个方法的。
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Creates a thread local variable. The initial value of the variable is
     * determined by invoking the {@code get} method on the {@code Supplier}.
     * -- 创建线程局部变量。变量的初始值是通过调用 {@code Supplier} 上的 {@code get} 方法确定的。
     *
     * @param <S> the type of the thread local's value
     * @param supplier the supplier to be used to determine the initial value
     * @return a new thread local variable
     * @throws NullPointerException if the specified supplier is null
     * @since 1.8
     */
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        // 无非就是使用了jdk 8 的 supplier 而已
        return new SuppliedThreadLocal<>(supplier);
    }

    /**
     * Creates a thread local variable.
     *
     * @see #withInitial(java.util.function.Supplier)
     */
    public ThreadLocal() {
    }

    /**
     * Returns the value in the current thread's copy of this
     * thread-local variable.  If the variable has no value for the
     * current thread, it is first initialized to the value returned
     * by an invocation of the {@link #initialValue} method.
     *
     * 返回当前线程与当前ThreadLocal对象相关联的 线程局部变量，这个变量只有当前线程能访问到。
     * 如果当前线程 没有分配，则给当前线程去分配（使用initialValue方法）
     *
     * @return the current thread's value of this thread-local
     *
     * //线程获取threadLocal.get()时 如果是第一次在某个 threadLocal对象上get时，会给当前线程分配一个value
     * //这个value 和 当前的threadLocal对象 被包装成为一个 entry 其中 key是 threadLocal对象，value是threadLocal对象给当前线程生成的value
     * //这个entry存放到 当前线程 threadLocals 这个map的哪个桶位？ 与当前 threadLocal对象的threadLocalHashCode 有关系。
     * // 使用 threadLocalHashCode & (table.length - 1) 的到的位置 就是当前 entry需要存放的位置。
     */
    public T get() {
        //获取当前线程
        Thread t = Thread.currentThread();
        //获取到当前线程Thread对象的 threadLocals map引用
        //return Thread.currentThread().threadLocals;
        ThreadLocalMap map = getMap(t);
        //条件成立：说明当前线程已经拥有自己的 ThreadLocalMap 对象了
        if (map != null) {
            //key：当前threadLocal对象
            //调用map.getEntry() 方法 获取threadLocalMap 中该threadLocal关联的 entry
            ThreadLocalMap.Entry e = map.getEntry(this);
            //条件成立：说明当前线程 初始化过 与当前threadLocal对象相关联的 线程局部变量
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T) e.value;
                //返回value
                return result;
            }
        }

        //执行到这里有几种情况？
        //1.当前线程对应的threadLocalMap是空
        //2.当前线程与当前threadLocal对象没有生成过相关联的 线程局部变量..

        //setInitialValue方法初始化当前线程与当前threadLocal对象 相关联的value。
        //且 当前线程如果没有threadLocalMap的话，还会初始化创建map。
        return setInitialValue();
    }

    /**
     * Variant of set() to establish initialValue. Used instead
     * of set() in case user has overridden the set() method.
     *
     * -- 用于建立初始值的 set() 变体。如果用户覆盖了 set() 方法，则代替 set() 使用。
     *
     * setInitialValue方法初始化当前线程与当前threadLocal对象 相关联的value。
     * 且 当前线程如果没有threadLocalMap的话，还会初始化创建map。
     *
     * @return the initial value
     */
    private T setInitialValue() {
        //调用的当前ThreadLocal对象的initialValue方法，这个方法 大部分情况下咱们都会重写。如果不重写，则value默认为null
        //value 就是当前ThreadLocal对象与当前线程相关联的 线程局部变量。
        T value = initialValue();
        //获取当前线程对象
        Thread t = Thread.currentThread();
        //获取当前线程内部的threadLocals    threadLocalMap对象。
        ThreadLocalMap map = getMap(t);

        if (map != null) {
            //条件成立：说明当前线程内部已经初始化过 threadLocalMap对象了。 （线程的threadLocals 只会初始化一次。）
            //保存当前threadLocal与当前线程生成的 线程局部变量。
            //key: 当前threadLocal对象   value：线程与当前threadLocal相关的局部变量
            map.set(
                    this, // key: 当前threadLocal对象
                    value // value：线程与当前threadLocal相关的局部变量-- 要保存的对象
            );
        } else {
            //执行到这里，说明 当前线程内部还未初始化 threadLocalMap ，这里调用createMap 给当前线程创建map
            //参数1：当前线程   参数2：线程与当前threadLocal相关的局部变量
            createMap(
                    t,      // 参数1：当前线程
                    value   // 参数2：线程与当前threadLocal相关的局部变量
            );
        }

        //返回线程与当前threadLocal相关的局部变量
        return value;
    }

    /**
     * Sets the current thread's copy of this thread-local variable to the specified value.
     * -- 将此线程局部变量的当前线程副本设置为指定值。
     *
     * Most subclasses will have no need to override this method,
     * relying solely on the {@link #initialValue}
     * method to set the values of thread-locals.
     * -- 大多数子类将不需要覆盖此方法，仅依靠 {@link #initialValue} 方法来设置线程局部变量的值。
     *
     * 修改当前线程与当前threadLocal对象相关联的 线程局部变量。
     *
     * @param value the value to be stored in the current thread's copy of
     * this thread-local.
     */
    public void set(T value) {
        //获取当前线程
        Thread t = Thread.currentThread();
        //获取当前线程的threadLocalMap对象
        ThreadLocalMap map = getMap(t);

        if (map != null) {
            //条件成立：说明当前线程的threadLocalMap已经初始化过了
            //调用threadLocalMap.set方法 进行重写 或者 添加。
            map.set(this, value);
        } else {
            //执行到这里，说明当前线程还未创建 threadLocalMap对象。
            //参数1：当前线程   参数2：线程与当前threadLocal相关的局部变量
            createMap(t, value);
        }
    }

    /**
     * Removes the current thread's value for this thread-local
     * variable.  If this thread-local variable is subsequently
     * {@linkplain #get read} by the current thread, its value will be
     * reinitialized by invoking its {@link #initialValue} method,
     * unless its value is {@linkplain #set set} by the current thread
     * in the interim.  This may result in multiple invocations of the
     * {@code initialValue} method in the current thread.
     *
     * 移除当前线程与当前threadLocal对象相关联的 线程局部变量。
     *
     * @since 1.5
     */
    public void remove() {
        Thread currentThread = Thread.currentThread();
        //获取当前线程的 threadLocalMap对象
        ThreadLocalMap map = getMap(currentThread);
        if (map != null) {
            //条件成立：说明当前线程已经初始化过 threadLocalMap对象了

            //调用threadLocalMap.remove( key = 当前threadLocal)
            map.remove(this);
        }
    }

    /**
     * Get the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param t the current thread
     * @return the map
     */
    ThreadLocalMap getMap(Thread t) {
        //返回当前线程的 threadLocals
        return t.threadLocals;
    }

    /**
     * Create the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * // 参数1：当前线程
     * // 参数2：线程与当前threadLocal相关的局部变量
     *
     * @param t the current thread
     * @param firstValue value for the initial entry of the map
     */
    void createMap(Thread t, T firstValue) {
        //传递t 的意义就是 要访问 当前这个线程 t.threadLocals 字段，给这个字段初始化。

        //new ThreadLocalMap(this, firstValue)
        //创建一个ThreadLocalMap对象 初始 k-v 为 ： this <当前threadLocal对象> ，线程与当前threadLocal相关的局部变量
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * Factory method to create map of inherited thread locals.
     * -- 创建继承线程局部变量映射的工厂方法。
     *
     * Designed to be called only from Thread constructor.
     *
     * @param parentMap the map associated with parent thread
     * @return a map containing the parent's inheritable bindings
     * @see InheritableThreadLocal 支持继承的实现
     */
    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        return new ThreadLocalMap(parentMap);
    }

    /**
     * Method childValue is visibly defined in subclass
     * InheritableThreadLocal, but is internally defined here for the
     * sake of providing createInheritedMap factory method without
     * needing to subclass the map class in InheritableThreadLocal.
     * This technique is preferable to the alternative of embedding
     * instanceof tests in methods.
     *
     * -- 方法childValue明显是在子类InheritableThreadLocal中定义的，
     * 但是在这里内部定义是为了提供createInheritedMap工厂方法，而不需要在InheritableThreadLocal中子类化map类。
     * 这种技术优于在方法中嵌入 instanceof 测试的替代方法。
     */
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * An extension of ThreadLocal that obtains its initial value from
     * the specified {@code Supplier}.
     * -- ThreadLocal 的扩展，它从指定的 {@code Supplier} 获取其初始值。
     */
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            /**
             * 从传入的 supplier 中获取初始值
             */
            return supplier.get();
        }
    }

    /**
     * ThreadLocalMap is a customized hash map suitable only for
     * maintaining thread local values. No operations are exported
     * outside of the ThreadLocal class. The class is package private to
     * allow declaration of fields in class Thread.  To help deal with
     * very large and long-lived usages, the hash table entries use
     * WeakReferences for keys. However, since reference queues are not
     * used, stale entries are guaranteed to be removed only when
     * the table starts running out of space.
     *
     *
     * ThreadLocalMap 是一个定制的自定义 hashMap 哈希表，只适合用于维护
     * 线程对应ThreadLocal的值. 此类的方法没有在ThreadLocal 类外部暴露，
     * 此类是私有的，允许在 Thread 类中以字段的形式声明 ，
     * 以助于处理存储量大，生命周期长的使用用途，
     * 此类定制的哈希表实体键值对使用弱引用WeakReferences 作为key，
     * 但是, 一旦引用不在被使用，只有当哈希表中的空间被耗尽时(意思就是垃圾回收的时候)，对应不再使用的键值对实体才会确保被移除回收。
     */
    static class ThreadLocalMap {

        /**
         * The entries in this hash map extend WeakReference, using
         * its main ref field as the key (which is always a
         * ThreadLocal object).
         * -- 这个哈希映射中的条目扩展了 WeakReference，使用它的主要 ref 字段作为键（它总是一个 ThreadLocal 对象）。
         *
         * @see Reference#referent
         *
         * Note that null keys (i.e. entry.get()
         * == null) mean that the key is no longer referenced, so the
         * entry can be expunged from table.
         * -- 请注意，空键（即 entry.get() == null）意味着不再引用该键，因此可以从表中删除该条目。
         * -- 也就是说，ThreadLocal 不能是NUll，在实际使用的时候，它当然不会是null
         *
         * Such entries are referred to
         * as "stale entries" in the code that follows.
         * -- 此类条目在以下代码中称为“陈旧条目”。
         *
         * ================= ================= ================= ================= ================= ================= =================
         *
         * 什么是弱引用呢？
         * A a = new A();     // 强引用
         * WeakReference weakA = new WeakReference(a);  // 弱引用
         * 此时如果设置
         * a = null;
         * 下一次GC 时 对象a就被回收了，别管有没有 弱引用 是否在关联这个对象，都会本回收！！！！
         *
         * key 使用的是弱引用保留，key 保存的是 threadLocal 对象。
         * value 使用的是强引用，value保存的是 threadLocal 对象！！！与当前线程！！！相关联的 value。
         *
         * entry#key 这样设计有什么好处呢？
         * 当threadLocal对象失去强引用且对象GC回收后，散列表中的与 该threadLocal对象相关联的 entry#key 再次去key.get() 时，拿到的是null。
         * 站在map角度就可以区分出哪些entry是过期的，哪些entry是非过期的。
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {

            /**
             * The value associated with this ThreadLocal.
             * -- 与此 ThreadLocal 关联的值。
             */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                //key 传递给父类，弱引用中持有的key就是地区ThreadLocal对象
                /**
                 * @see Reference#get() 存入的Key，通过该方法可以拿到
                 */
                super(k);
                value = v;
            }
        }

        /**
         * The initial capacity -- MUST be a power of two.
         * 初始化当前map内部 散列表数组的初始长度 16
         */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * The table, resized as necessary.
         * table.length ！！！！MUST！！！！！ always be a power of two.
         * threadLocalMap 内部散列表数组引用，数组的长度 必须是 2的次方数
         *
         * 这样设计的目的也是为了提高性能，更快的取模
         */
        private Entry[] table;

        /**
         * The number of entries in the table.
         * 当前散列表数组 占用情况，存放多少个entry。
         */
        private int size = 0;

        /**
         * The next size value at which to resize.
         * -- 要调整大小的下一个大小值。（下次要扩容的阈值）
         *
         * 扩容触发阈值，初始值为： len * 2/3 （16 * 2 / 3）
         * 触发后调用 rehash() 方法。扩容之后当然要进行重新hash，重新分配嘈位
         *
         *
         * rehash() 方法先做一次全量检查全局 过期数据，把散列表中所有过期的entry移除。
         * 如果移除之后 当前 散列表中的entry 个数仍然达到  threshold - threshold/4  就进行扩容。
         */
        private int threshold; // Default to 0

        /**
         * Set the resize threshold to maintain at worst a 2/3 load factor.
         *
         * threshold = (len + 2) / 3;
         * 存储数据的数量 >= threshold 的时候会扩容
         * 将阈值设置为 （当前数组长度 * 2）/ 3。
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * Increment i modulo len.
         * 参数1：当前下标
         * 参数2：当前散列表数组长度
         */
        private static int nextIndex(int i, int len) {
            //当前下标+1 小于散列表数组的话，返回 +1后的值
            //否则 情况就是 下标+1 == len ，返回0
            //实际形成一个环绕式的访问。
            return (
                    (i + 1 < len) ? // 如果再添加一个entry到数组中，数组长度是否越界？
                            i + 1   // 如果再添加一个entry到数组中，还不至于导致数组长度越界，那么就直接放到下一个空位中即可
                            :
                            0       // 如果再添加一个entry到数组中，导致数组长度越界，那么就循环至数组的头部
                    //超过了下标的最大值(len - 1)，则从头开始，循环到开头的下标0
            );
        }

        /**
         * Decrement i modulo len.
         * 参数1：当前下标
         * 参数2：当前散列表数组长度
         */
        private static int prevIndex(int i, int len) {
            //当前下标-1 大于等于0 返回 -1后的值就ok。
            //否则 说明 当前下标-1 == -1. 此时 返回散列表最大下标。
            //实际形成一个环绕式的访问。
            return (
                    (i - 1 >= 0) ?//前一个下标是否 >= 0？
                            i - 1 //如果前一个下标还是合法的，则直接使用
                            :
                            len - 1 //否则，循环到数组的最后一个下标
                    // 反正就是循环利用这个数组
            );
        }

        /**
         * Construct a new map initially containing (firstKey, firstValue).
         * ThreadLocalMaps are constructed lazily, so we only create
         * one when we have at least one entry to put in it.
         * -- 构造一个最初包含 (firstKey, firstValue) 的新映射。 ThreadLocalMaps 是惰性构造的，所以我们只有在至少有一个条目可以放入时才创建一个。
         *
         * 因为Thread.threadLocals 字段是 延迟初始化的，只有线程第一次存储 threadLocal-value 时 才会创建 threadLocalMap对象。
         *
         * firstKey :threadLocal对象
         * firstValue: 当前线程与threadLocal对象关联的value。
         */
        ThreadLocalMap(
                ThreadLocal<?> firstKey, // threadLocal对象
                Object firstValue   //当前线程与threadLocal对象关联的value。
        ) {
            //创建entry数组长度为16，表示threadLocalMap内部的散列表。
            table = new Entry[INITIAL_CAPACITY];
            //寻址算法：key.threadLocalHashCode & (table.length - 1)
            //table数组的长度一定是 2 的次方数。
            //2的次方数-1 有什么特征呢？  转化为2进制后都是1.    16==> 1 0000 - 1 => 1111
            //1111 与任何数值进行&运算后 得到的数值 一定是 <= 1111
            //index 计算出来的结果 一定是 <= B1111
            int index = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);

            //创建entry对象 存放到 指定位置的slot中。
            table[index] = new Entry(firstKey, firstValue);
            //设置size=1,第一次
            size = 1;
            //设置扩容阈值 （当前数组长度 * 2）/ 3  => 16 * 2 / 3 => 10
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * Construct a new map including all Inheritable ThreadLocals
         * from given parent map. Called only by createInheritedMap.
         *
         * -- 构造一个新 ThreadLocalMap，其中包括给定父 ThreadLocalMap 中的
         * 所有可继承ThreadLocals。仅由createInheritedMap调用。
         *
         * @param parentMap the map associated with parent thread.
         */
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            for (int j = 0; j < len; j++) {
                Entry e = parentTable[j];

                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        Entry entry = new Entry(key, value);
                        int index = key.threadLocalHashCode & (len - 1);
                        //在新 ThreadLocalMap 数组中重新分配槽位！！
                        while (table[index] != null) {
                            // 找到一个槽位
                            index = nextIndex(index, len);
                        }
                        table[index] = entry;
                        size++;
                    }
                }
            }
        }

        /**
         * Get the entry associated with key.  This method
         * itself handles only the fast path: a direct hit of existing
         * key. It otherwise relays to getEntryAfterMiss.  This is
         * designed to maximize performance for direct hits, in part
         * by making this method readily inlinable.
         *
         * ThreadLocal对象 get() 操作 实际上是由 ThreadLocalMap.getEntry() 代理完成的。
         *
         * key:某个 ThreadLocal对象，因为 散列表中存储的entry.key 类型是 ThreadLocal。
         *
         * @param key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntry(ThreadLocal<?> key) {
            //路由规则： ThreadLocal.threadLocalHashCode & (table.length - 1) ==> index
            //定位该 key 在该线程的 table[] 中的下标
            int index = key.threadLocalHashCode & (table.length - 1);
            //访问散列表中 指定指定位置的 slot
            Entry e = table[index];

            if (
                    e != null //条件一：成立 说明slot有值
                            &&
                            e.get() == key //条件二：成立 说明 entry#key 与当前查询的key一致，返回当前entry 给上层就可以了。
            ) {

                // 返回词条
                return e;
            } else {
                // 有几种情况会执行到这里？
                // 1.e == null
                // 2.e.key != key (表示发生了hash冲突)

                // getEntryAfterMiss 方法 会继续向当前桶位后面继续搜索 e.key == key 的entry.

                // 为什么这样做呢？？
                // 因为 存储时  发生hash冲突后，并没有在entry层面形成 链表..
                // 存储时的处理 就是线性的向后找到一个可以使用的slot，并且存放进去。
                return getEntryAfterMiss(key, index, e);
            }
        }

        /**
         * Version of getEntry method for use when key is not found in
         * its direct hash slot.
         *
         * @param key the thread local object           threadLocal对象 表示key
         * @param index the table index for key's hash code  key计算出来的index
         * @param entry the entry at table[i]                table[index] 中的 entry
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntryAfterMiss(
                ThreadLocal<?> key, // 要查询的key
                int index,  // int index = key.threadLocalHashCode & (table.length - 1);
                Entry entry // Entry e = table[index];
        ) {
            //获取当前threadLocalMap中的散列表 table
            Entry[] tab = table;
            //获取table长度
            int len = tab.length;

            //条件：entry != null 说明 向后查找的范围是有限的，碰到 slot == null 的情况，搜索结束。
            //entry:循环处理的当前元素
            while (entry != null) {
                //获取当前slot 中entry对象的key
                ThreadLocal<?> k = entry.get();
                //条件成立：说明向后查询过程中找到合适的entry了，返回entry就ok了。
                if (k == key) {
                    //找到的情况下，就从这里返回了。
                    return entry;
                }
                //条件成立：说明当前slot中的entry#key 关联的 ThreadLocal对象已经被GC回收了.. 因为key 是弱引用， key = e.get() == null.
                if (k == null) {//e != null && k == null 说明此处的 k 已经被回收
                    //做一次 探测式过期数据回收。
                    expungeStaleEntry(index);
                } else {
                    //更新index，继续向后搜索。
                    index = nextIndex(index, len);
                }
                //获取下一个slot中的entry。
                entry = tab[index];
            }
            //执行到这里，说明关联区段内都没找到相应数据。
            return null;
        }

        /**
         * Set the value associated with key.
         *
         * ThreadLocal 使用set方法 给当前线程添加 threadLocal-value   键值对。
         *
         * @param key the thread local object
         * @param value the value to be set
         */
        private void set(ThreadLocal<?> key, Object value) {
            //获取散列表
            Entry[] tab = table;
            //获取散列表数组长度
            int len = tab.length;
            //计算当前key 在 散列表中的对应的位置
            int indexOfKey = key.threadLocalHashCode & (len - 1);

            //以当前key对应的slot位置 向后查询，找到可以使用的slot。
            //什么slot可以使用呢？？
            //1.k == key 说明是替换
            //2.碰到一个过期的 slot ，这个时候 咱们可以强行占用呗。
            //3.查找过程中 碰到 slot == null 了。
            for (Entry entry = tab[indexOfKey];
                 entry != null;//循环，知道遇到 entry == null 为止
                 entry = tab[indexOfKey = nextIndex(indexOfKey, len)]) {

                //获取当前元素key
                ThreadLocal<?> threadLocalKey = entry.get();

                //条件成立：说明当前set操作是一个替换操作。
                if (threadLocalKey == key) {
                    //做替换逻辑。
                    entry.value = value;
                    return;
                }

                if (threadLocalKey == null) {//条件成立：说明 向下寻找过程中 碰到 entry#key == null 的情况了，说明当前entry 是过期数据。
                    //碰到一个过期的 slot ，这个时候 咱们可以强行占用呗。
                    //替换过期数据的逻辑。
                    replaceStaleEntry(key, value, indexOfKey);
                    return;
                }
            }//跳出循环，则说明在循环过程中，既没有找到替换的key也没有找到过期的槽位，则说明本次是新增

            //执行到这里，说明for循环碰到了 slot == null 的情况。也就是 该slot 中的 entry为null
            //在合适的slot中 创建一个新的entry对象。
            tab[indexOfKey] = new Entry(key, value);
            //因为是新添加 所以++size.
            int sz = ++size;

            //做一次启发式清理
            //条件一：!cleanSomeSlots(i, sz) 成立，说明启发式清理工作 未清理到任何数据..
            //条件二：sz >= threshold 成立，说明当前table内的entry已经达到扩容阈值了..会触发rehash操作。
            if (!cleanSomeSlots(indexOfKey, sz) && sz >= threshold) {
                rehash();
            }
        }

        /**
         * Remove the entry for key.
         */
        private void remove(ThreadLocal<?> key) {
            Entry[] tab = table;
            int len = tab.length;
            int index = key.threadLocalHashCode & (len - 1);
            for (Entry entry = tab[index]; entry != null; entry = tab[index = nextIndex(index, len)]) {
                if (entry.get() == key) {
                    entry.clear();
                    expungeStaleEntry(index);
                    return;
                }
            }
        }

        /**
         * Replace a stale entry encountered during a set operation
         * with an entry for the specified key.  The value passed in
         * the value parameter is stored in the entry, whether or not
         * an entry already exists for the specified key.
         *
         * As a side effect, this method expunges all stale entries in the
         * "run" containing the stale entry.  (A run is a sequence of entries
         * between two null slots.)
         *
         * @param key the key 当前要插入的key
         * @param value the value to be associated with key 当前要添加的value
         * @param staleSlot index of the first stale entry encountered while 该下标的词条的key为null，需要替换它
         * searching for key.
         * key: 键 threadLocal对象
         * value: val
         * staleSlot: 上层方法 set方法，迭代查找时 发现的当前这个slot是一个过期的 entry。
         */
        private void replaceStaleEntry(ThreadLocal<?> key, Object value, int staleSlot) {
            //获取散列表
            Entry[] tab = table;
            //获取散列表数组长度
            int len = tab.length;
            //临时变量
            Entry entry;

            //表示 开始探测式清理过期数据的 开始下标。默认从当前 staleSlot开始。
            int slotToExpunge = staleSlot;

            //以当前staleSlot开始 向前迭代查找，找有没有过期的数据。for循环一直到碰到null结束。
            //从下标为 staleSlot的前一位开始迭代，知道 当前槽位 的 词条不为空结束
            for (int i = prevIndex(staleSlot, len); (entry = tab[i]) != null; i = prevIndex(i, len)) {
                if (entry.get() == null) {//条件成立：说明向前找到了过期数据，更新 探测清理过期数据的开始下标为 i
                    slotToExpunge = i;
                }
            }

            //TODO 以当前staleSlot向后去查找，直到碰到null为止。
            for (int i = nextIndex(staleSlot, len); (entry = tab[i]) != null; i = nextIndex(i, len)) {
                //获取当前元素 key
                ThreadLocal<?> k = entry.get();

                //条件成立：说明咱们是一个 替换逻辑。
                if (k == key) {
                    //替换新数据。
                    entry.value = value;

                    //交换位置的逻辑..
                    //将table[staleSlot]这个过期数据 放到 当前循环到的 table[i] 这个位置。
                    tab[i] = tab[staleSlot];
                    //将tab[staleSlot] 中保存为 当前entry。 这样的话，咱们这个数据位置就被优化了..
                    tab[staleSlot] = entry;

                    //条件成立：
                    // 1.说明 replaceStaleEntry 一开始时 的向前查找过期数据 并未找到过期的entry.
                    // 2.向后检查过程中也未发现过期数据..
                    if (slotToExpunge == staleSlot) {
                        //开始探测式清理过期数据的下标 修改为 当前循环的index。
                        slotToExpunge = i;
                    }

                    //cleanSomeSlots ：启发式清理
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                //条件1：k == null 成立，说明当前遍历的entry是一个过期数据..
                //条件2：slotToExpunge == staleSlot 成立，一开始时 的向前查找过期数据 并未找到过期的entry.
                if (k == null && slotToExpunge == staleSlot) {
                    //因为向后查询过程中查找到一个过期数据了，更新slotToExpunge 为 当前位置。
                    //前提条件是 前驱扫描时 未发现 过期数据..

                    slotToExpunge = i;
                }
            }

            //什么时候执行到这里呢？
            //向后查找过程中 并未发现 k == key 的entry，说明当前set操作 是一个添加逻辑..

            //直接将新数据添加到 table[staleSlot] 对应的slot中。
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);

            //条件成立：除了当前staleSlot 以外 ，还发现其它的过期slot了.. 所以要开启 清理数据的逻辑..
            if (slotToExpunge != staleSlot) {
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
            }
        }

        /**
         * Expunge a stale entry by rehashing any possibly colliding entries
         * lying between staleSlot and the next null slot.  This also expunges
         * any other stale entries encountered before the trailing null.  See
         * Knuth, Section 6.4
         *
         * 参数 staleSlot   table[staleSlot] 就是一个过期数据，以这个位置开始 继续向后查找过期数据，直到碰到 slot == null 的情况结束。
         *
         * 此方法为清理数据，重新分配槽位
         *
         * @param staleSlot index of slot known to have null key -- 已知具有空键的插槽索引
         * @return the index of the next null slot after staleSlot
         * (all between staleSlot and this slot will have been checked
         * for expunging).
         *
         * 此处（下标为 staleSlot 的 Entry ）的 entry 不为 null，但是 key 为 null , 这个 entry 是要回收的
         *
         * expung 清除
         * stale 陈腐的；不新鲜的
         *
         * 清除工作
         */
        private int expungeStaleEntry(int staleSlot // 已知具有空键的插槽索引 staleSlot
        ) {
            //获取散列表
            Entry[] tab = table;
            //获取散列表当前长度
            int len = tab.length;

            // expunge entry at staleSlot
            // help gc，因为key已经被回收了，而value没有，此处直接设置为null
            tab[staleSlot].value = null;
            //因为staleSlot位置的entry 是过期的 这里直接置为Null
            tab[staleSlot] = null;
            //因为上面干掉一个元素，所以 -1.
            size--;

            // Rehash until we encounter null -- 重新哈希直到我们遇到 null
            // e：表示当前遍历节点
            Entry e;
            //i：表示当前遍历的index
            int i;

            //for循环从 staleSlot + 1的位置开始搜索过期数据，直到碰到 slot == null 结束。
            for (
                    i = nextIndex(staleSlot, len);//i = staleSlot 的下一个开始
                    (e = tab[i]) != null; //结束条件为： tab[i] == null
                    i = nextIndex(i, len) //下一次循环的下标 +  1
            ) {

                //进入到for循环里面 当前entry一定不为null

                //获取当前遍历节点 entry 的key.
                ThreadLocal<?> k = e.get();

                if (k == null) {
                    //条件成立：说明k表示的threadLocal对象 已经被GC回收了... 当前entry属于脏数据了...

                    //处理过期槽位

                    //help gc
                    e.value = null;
                    //脏数据对应的slot置为null
                    tab[i] = null;
                    //因为上面干掉一个元素，所以 -1.
                    size--;
                } else {
                    //执行到这里，说明当前遍历的slot中对应的entry 是非过期数据(因为 k != null)
                    //因为前面有可能清理掉了几个过期数据。
                    //且当前entry 存储时有可能碰到hash冲突了，往后偏移存储了，这个时候 应该去优化位置，让这个位置更靠近 正确位置。
                    //这样的话，查询的时候 效率才会更高！

                    //重新计算当前entry对应的 index 下标
                    //下标h本来应该是该 threadLocal 的存储下标，但是可能发生了冲突，该 threadLocal 存储的位置向后移动了
                    int h = k.threadLocalHashCode & (len - 1);

                    if (h != i) {
                        //条件成立：说明当前entry存储时 就是发生过hash冲突，然后向后偏移过了.

                        //将entry当前位置 设置为null
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.

                        //h 是正确位置。

                        //以正确位置h 开始，向后查找第一个 可以存放entry的位置。
                        while (tab[h] != null) {
                            h = nextIndex(h, len);
                        }

                        //将当前元素放入到 距离正确位置 更近的位置（有可能就是正确位置）。
                        tab[h] = e;
                    }
                }
            }
            return i;
        }

        /**
         * Heuristically scan some cells looking for stale entries.
         * This is invoked when either a new element is added, or
         * another stale one has been expunged. It performs a
         * logarithmic number of scans, as a balance between no
         * scanning (fast but retains garbage) and a number of scans
         * proportional to number of elements, that would find all
         * garbage but would cause some insertions to take O(n) time.
         *
         * @param i a position known NOT to hold a stale entry. The
         * scan starts at the element after i.
         * @param n scan control: {@code log2(n)} cells are scanned,
         * unless a stale entry is found, in which case
         * {@code log2(table.length)-1} additional cells are scanned.
         * When called from insertions, this parameter is the number
         * of elements, but when from replaceStaleEntry, it is the
         * table length. (Note: all this could be changed to be either
         * more or less aggressive by weighting n instead of just
         * using straight log n. But this version is simple, fast, and
         * seems to work well.)
         *
         * 参数 i 启发式清理工作开始位置
         * 参数 n 一般传递的是 table.length ，这里n 也表示结束条件。
         * @return true if any stale entries have been removed.
         */
        private boolean cleanSomeSlots(int i, int n) {
            //表示启发式清理工作 是否清除了过期数据
            boolean removed = false;
            //获取当前map的散列表引用
            Entry[] tab = table;
            //获取当前散列表数组长度
            int len = tab.length;

            do {
                //这里为什么不是从i就检查呢？
                //因为cleanSomeSlots(i = expungeStaleEntry(???), n)  expungeStaleEntry(???) 返回值一定是null。

                //获取当前i的下一个 下标
                i = nextIndex(i, len);
                //获取table中当前下标为i的元素
                Entry e = tab[i];
                //条件一：e != null 成立
                //条件二：e.get() == null 成立，说明当前slot中保存的entry 是一个过期的数据..
                if (e != null && e.get() == null) {
                    //重新更新n为 table数组长度
                    n = len;
                    //表示清理过数据.
                    removed = true;
                    //以当前过期的slot为开始节点 做一次 探测式清理工作
                    i = expungeStaleEntry(i);
                }

                // 假设table长度为16
                // 16 >>> 1 ==> 8
                // 8 >>> 1 ==> 4
                // 4 >>> 1 ==> 2
                // 2 >>> 1 ==> 1
                // 1 >>> 1 ==> 0
            } while ((n >>>= 1) != 0);

            return removed;
        }

        /**
         * Re-pack and/or re-size the table. First scan the entire
         * table removing stale entries. If this doesn't sufficiently
         * shrink the size of the table, double the table size.
         */
        private void rehash() {
            //这个方法执行完后，当前散列表内的所有过期的数据，都会被干掉。
            expungeStaleEntries();

            // Use lower threshold for doubling to avoid hysteresis
            //条件成立：说明清理完 过期数据后，当前散列表内的entry数量仍然达到了 threshold * 3/4，真正触发 扩容！
            if (size >= threshold - threshold / 4) {
                //扩容。
                resize();
            }
        }

        /**
         * Double the capacity of the table.
         *
         * -- 容量翻倍。
         */
        private void resize() {
            //获取当前散列表
            Entry[] oldTab = table;
            //获取当前散列表长度
            int oldLen = oldTab.length;
            //计算出扩容后的表大小  oldLen * 2
            int newLen = oldLen * 2;
            //创建一个新的散列表
            Entry[] newTab = new Entry[newLen];
            //表示新table中的entry数量。
            int count = 0;

            //遍历老表 迁移数据到新表。
            for (int j = 0; j < oldLen; ++j) {
                //访问老表的指定位置的slot
                Entry e = oldTab[j];
                //条件成立：说明老表中的指定位置 有数据
                if (e != null) {
                    //获取entry#key
                    ThreadLocal<?> k = e.get();
                    //条件成立：说明老表中的当前位置的entry 是一个过期数据..
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        //执行到这里，说明老表的当前位置的元素是非过期数据 正常数据，需要迁移到扩容后的新表。。

                        //计算出当前entry在扩容后的新表的 存储位置。
                        int h = k.threadLocalHashCode & (newLen - 1);
                        //while循环 就是拿到一个距离h最近的一个可以使用的slot。
                        while (newTab[h] != null) {
                            h = nextIndex(h, newLen);
                        }

                        //将数据存放到 新表的 合适的slot中。
                        newTab[h] = e;
                        //数量+1
                        count++;
                    }
                }
            }

            //设置下一次触发扩容的指标。
            setThreshold(newLen);
            size = count;
            //将扩容后的新表 的引用保存到 threadLocalMap 对象的 table这里。。
            table = newTab;
        }

        /**
         * Expunge all stale entries in the table.
         */
        private void expungeStaleEntries() {
            Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                Entry entry = tab[j];
                if (
                        entry != null // 说明该槽位存储的词条没有被清除
                                && entry.get() == null // 说明该词条对应的key已经被回收
                ) {

                    // 进入这里说明：j位置的词条需要被清除
                    expungeStaleEntry(j);
                }
            }
        }
    }
}
