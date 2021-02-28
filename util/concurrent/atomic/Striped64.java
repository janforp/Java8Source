package java.util.concurrent.atomic;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongBinaryOperator;

/**
 * A package-local class holding common representation and mechanics
 * for classes supporting dynamic striping on 64bit values. The class
 * extends Number so that concrete subclasses must publicly do so.
 *
 * Striped:有条纹的
 */
@SuppressWarnings({ "serial", "all" })
abstract class Striped64 extends Number {
    /*
     * This class maintains a lazily-initialized table of atomically
     * updated variables, plus an extra "base" field. The table size
     * is a power of two. Indexing uses masked per-thread hash codes.
     * Nearly all declarations in this class are package-private,
     * accessed directly by subclasses.
     *
     * Table entries are of class Cell; a variant of AtomicLong padded
     * (via @sun.misc.Contended) to reduce cache contention. Padding
     * is overkill for most Atomics because they are usually
     * irregularly scattered in memory and thus don't interfere much
     * with each other. But Atomic objects residing in arrays will
     * tend to be placed adjacent to each other, and so will most
     * often share cache lines (with a huge negative performance
     * impact) without this precaution.
     *
     * In part because Cells are relatively large, we avoid creating
     * them until they are needed.  When there is no contention, all
     * updates are made to the base field.  Upon first contention (a
     * failed CAS on base update), the table is initialized to size 2.
     * The table size is doubled upon further contention until
     * reaching the nearest power of two greater than or equal to the
     * number of CPUS. Table slots remain empty (null) until they are
     * needed.
     *
     * A single spinlock ("cellsBusy") is used for initializing and
     * resizing the table, as well as populating slots with new Cells.
     * There is no need for a blocking lock; when the lock is not
     * available, threads try other slots (or the base).  During these
     * retries, there is increased contention and reduced locality,
     * which is still better than alternatives.
     *
     * The Thread probe fields maintained via ThreadLocalRandom serve
     * as per-thread hash codes. We let them remain uninitialized as
     * zero (if they come in this way) until they contend at slot
     * 0. They are then initialized to values that typically do not
     * often conflict with others.  Contention and/or table collisions
     * are indicated by failed CASes when performing an update
     * operation. Upon a collision, if the table size is less than
     * the capacity, it is doubled in size unless some other thread
     * holds the lock. If a hashed slot is empty, and lock is
     * available, a new Cell is created. Otherwise, if the slot
     * exists, a CAS is tried.  Retries proceed by "double hashing",
     * using a secondary hash (Marsaglia XorShift) to try to find a
     * free slot.
     *
     * The table size is capped because, when there are more threads
     * than CPUs, supposing that each thread were bound to a CPU,
     * there would exist a perfect hash function mapping threads to
     * slots that eliminates collisions. When we reach capacity, we
     * search for this mapping by randomly varying the hash codes of
     * colliding threads.  Because search is random, and collisions
     * only become known via CAS failures, convergence can be slow,
     * and because threads are typically not bound to CPUS forever,
     * may not occur at all. However, despite these limitations,
     * observed contention rates are typically low in these cases.
     *
     * It is possible for a Cell to become unused when threads that
     * once hashed to it terminate, as well as in the case where
     * doubling the table causes no thread to hash to it under
     * expanded mask.  We do not try to detect or remove such cells,
     * under the assumption that for long-running instances, observed
     * contention levels will recur, so the cells will eventually be
     * needed again; and for short-lived ones, it does not matter.
     */

    /**
     * Padded variant of AtomicLong supporting only raw accesses plus CAS.
     * -- AtomicLong的填充变体仅支持原始访问以及CAS。
     *
     * JVM intrinsics note(JVM内在注释):
     * It would be possible to use a release-only form of CAS here, if it were provided.
     * -- 如果提供了CAS，则可以在此处使用仅发行形式的CAS。
     */
    @sun.misc.Contended
    static final class Cell {

        /**
         * 当前 cell 中存储的 value
         */
        volatile long value;

        Cell(long x) {
            //初始的value
            value = x;
        }

        /**
         * 修改当前 cell 中存储的 值
         *
         * @param cmp 修改之前的值(用于cas比较)
         * @param val 比较成功之后就把当前cell中的值设置为 val
         * @return 修改成功还是失败
         */
        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;

        /**
         * @see Cell#value Cell.class 中 {@link Cell#value} 字段的内存偏移
         */
        private static final long valueOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> ak = Cell.class;
                valueOffset = UNSAFE.objectFieldOffset(ak.getDeclaredField("value"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Number of CPUS, to place bound on table size
     * cpu核shu，cells(Cell 数组)长度关键影响因素
     *
     * @see Striped64#cells 影响该数组的长度
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Table of cells. When non-null, size is a power of 2.
     *
     * 该数组的长度必须是2的次方: 2^n
     */
    transient volatile Cell[] cells;

    /**
     * Base value, used mainly when there is no contention, but also as
     * a fallback during table initialization races. Updated via CAS.
     * 没有发生竞争的时候，数据类累加到base上，或者
     * 当cells扩容的时候，想要将数据写到base中
     */
    transient volatile long base;

    /**
     * Spinlock:自旋锁
     *
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     * -- Cell数组在调整大小和/或创建时使用的自旋锁（通过CAS锁定）。
     *
     * 自旋锁
     * 初始化cells或者扩容cells的时候都需要获取锁
     * 0：表示无锁状态
     * 1：表示其他线程已经持有锁类
     */
    transient volatile int cellsBusy /** 1/0 两个值 */
            ;

    /**
     * Package-private default constructor
     * 包私有，因为就 XXXAdder 在继承该类，其他包无法使用
     */
    Striped64() {
    }

    /**
     * CASes the base field.
     *
     * 通过cas的方式修改{@link Striped64#base} 的值
     */
    final boolean casBase(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, BASE, cmp, val);
    }

    /**
     * CASes the cellsBusy field from 0 to 1 to acquire lock.
     * 通过cas方式获取锁
     */
    final boolean casCellsBusy() {
        //只有在无锁状态才能拿到锁
        return UNSAFE.compareAndSwapInt(
                this, // 说明是当前对象
                CELLSBUSY, //锁字段的内存偏移
                0, //只有在锁还没有被占用的时候才能有机会获取锁，如果锁没有被占用，则锁字段的值为0
                1 //如果拿到了锁，则将锁字段的值设置为1
        );
    }

    /**
     * Returns the probe value for the current thread. - 返回当前线程的探测值。
     *
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     * -- 由于包限制，从ThreadLocalRandom复制。
     *
     * 获取当前线程的hash值
     */
    static final int getProbe() {
        return UNSAFE.getInt(
                Thread.currentThread(), // cas操作的对象为当前线程对象
                PROBE //获取int值对应字段的内存偏移
        );
    }

    /**
     * Pseudo:假的
     *
     * Pseudo-randomly advances and records the given probe value for the given thread.
     * -- 伪随机地前进并记录给定线程的给定探测值。
     *
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     * -- 由于包限制，从ThreadLocalRandom复制。
     *
     * 重置当前线程的hash值
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        UNSAFE.putInt(Thread.currentThread(), PROBE, probe);
        return probe;
    }

    /**
     * Handles cases of updates involving initialization, resizing,
     * creating new Cells, and/or contention. See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     *
     * @param addValue the value
     * @param fn the update function, or null for add (this convention
     * avoids the need for an extra field or function in LongAdder).
     * @param wasUncontended false if CAS failed before call
     *
     * 什么时候进来呢？
     * 条件1，2：true：说明cells未初始化，也就是多线程写base发生竞争了[猜测：重试，初始化cells数组]
     * 条件3为true:说明当前线程对应下标的cell为空，需要创建cell（longAccumulate中会创建）[猜测：创建]
     * 条件4为true：表示cas失败，意味着当前线程对应的cell有竞争[猜测：重试或者扩容]
     * TODO 画流程图
     *
     * 该方法主要做的事情就是：累加 long 类型的变量
     */
    final void longAccumulate(
            long addValue,//需要增加的数量
            LongBinaryOperator fn,//可以忽略

            /**
             * Uncontended : 没有竞争
             * wasUncontended：是否还没有发生过竞争,如果为
             *
             * true则表示还没有发生过竞争,
             *
             * false表示已经发生了竞争
             *
             * 表示是否发生过竞争，只有cells初始化之后(cells ！= null)，并且当前线程竞争修改失败，才会是false
             */
            boolean wasUncontended) {

        //当前线程的hash值
        int hashCodeOfCurrentThread;

        if ((hashCodeOfCurrentThread = getProbe()) == 0) {
            /**
             * 赋值 hashCodeOfCurrentThread
             * 如果当前线程的{@link Thread#threadLocalRandomProbe}变量还是0，表示当前线程的hash值还没有初始化=0
             * 说明当前线程还没有分配hash值
             *
             * @see ThreadLocalRandom#current() 该方法就是给当前线程的 {@link Thread#threadLocalRandomProbe}变量 初始化
             */
            //给当前线程分配hash值
            ThreadLocalRandom.current(); // force initialization
            /**
             * 再次赋值，此时肯定有值，并且不等于0
             *  @see ThreadLocalRandom#current() 该方法会赋值
             */
            hashCodeOfCurrentThread = getProbe();

            /**
             * wasUncontended = true : 表示还没有发生竞争
             *
             * 为什么？因为默认情况下，当前线程的hash值=0，当前线程肯定是写入到了cell[0](因为 hashcode 等于 0的时候，取模的值肯定是0)位置。
             * 不把他当做一次真正的竞争
             */
            wasUncontended = true;
        }

        /**
         * collide：碰撞
         * 表示扩容意向，false一定不会扩容，true可能扩容
         */
        boolean collide = false;                // True if last slot nonempty--如果最后一个插槽非空则为真

        /**** 自旋，目的是为了累加 long 值， start  ******/
        for (; ; ) {
            //表示cells引用
            Cell[] cellsReference;
            //表示当前线程命中的cell
            Cell hitCell;
            //表示cells数组长度
            int lenOfCells;
            //表示期望值
            long v;

            //as,n赋值
            //CASE1:表示cells已经初始化了，当前线程应该将数据写入到对应的cell中
            if ((cellsReference = cells) != null && (lenOfCells = cellsReference.length) > 0) {
                //条件3为true:说明当前线程对应下标的cell为空，需要创建cell（longAccumulate中会创建）[猜测：创建]
                //条件4为true：表示cas失败，意味着当前线程对应的cell有竞争[猜测：重试或者扩容]

                //CASE1.1:True:表示当前线程对应的下标位置的cell为null，需要创建 new Cell
                if ((hitCell = cellsReference[(lenOfCells - 1) & hashCodeOfCurrentThread]) == null) {

                    //true：表示当前锁未被占用，false表示锁被占用
                    if (cellsBusy == 0) {       // Try to attach new Cell

                        //拿当前的x创建cell
                        Cell newCell = new Cell(addValue);   // Optimistically create

                        if (cellsBusy == 0//true：表示当前锁未被占用，false表示锁被占用
                                &&
                                //拿锁操作，true:表示当前线程获取锁成功，false表示当前线程获取锁失败
                                casCellsBusy()) {//可能第一个条件判断完之后，此处cpu让出去了

                            //当前线程获取锁成功之后才进来这里

                            //是否创建成功的标记
                            boolean created = false;
                            try {               // Recheck under lock
                                //表示当前cells引用
                                Cell[] rs;
                                //m:cells长度
                                //j:表示当前线程命中的cell的下标
                                int m, j;
                                if ((rs = cells) != null//赋值，true：cells不为空，false：为空，此处肯定为true
                                        &&
                                        (m = rs.length) > 0///赋值，true：cells不为空，false：为空，此处肯定为true
                                        &&
                                        //赋值下标j，取模，重复判断，也就是双重检查
                                        //目的是位了防止其他线程初始化过该位置，然后当前线程再次初始化该位置，导致丢失数据
                                        rs[j = (m - 1) & hashCodeOfCurrentThread] == null) {
                                    rs[j] = newCell;
                                    created = true;
                                }
                            } finally {
                                //释放锁,此处就有并发问题，需要进行双重检查
                                cellsBusy = 0;
                            }
                            if (created) {
                                break;
                            }
                            continue;           // Slot is now non-empty
                        }
                    }

                    //如果当前锁被占用，这停止扩容意向
                    collide = false;
                }

                // CAS already known to fail
                //CASE1.2:只有一种情况：cells初始化后，并且当前线程竞争需改失败，才会是false
                else if (!wasUncontended) {
                    //竞争了
                    wasUncontended = true;      // Continue after rehash
                }

                //CASE1.3:当前线程 rehash 过 hash，然后新命中的cell不为空
                //true：表示写成功，这退出自旋即可
                //false：表示rehash之后命中的新的cell也有竞争，导致cas失败,重试了一次，再重试一次
                else if (hitCell.cas(v = hitCell.value, ((fn == null) ? v + addValue : fn.applyAsLong(v, addValue)))) {
                    break;
                }

                //CASE1.4：
                //条件一：n >= ncpu，true扩容意向改为false.表示不扩容了，false：说明cells数组还可以扩容
                //条件二：cells != as true表示其他线程已经扩容过了，当前线程rehash重试即可
                else if (lenOfCells >= NCPU || cells != cellsReference) {
                    //扩容意向改为false.表示不扩容了
                    collide = false;            // At max size or stale
                }

                //CASE1.5：
                //true的时候，即collide=false的时候，设置扩容意向为true，但是不一定真的发生扩容
                //
                else if (!collide) {
                    collide = true;
                }

                //CASE1.6:真正扩容的逻辑
                else if (cellsBusy == 0//条件一,true表示当前无锁状态，当前线程可以去竞争这把锁
                        && //非原子性的操作
                        //条件二，具体竞争锁的逻辑，true表示竞争到了锁，否则没有拿到锁，表示当前有其他线程在做扩容操作
                        casCellsBusy()) {

                    //只有当前无锁，并且当前线程获取到了锁，才进入到这里
                    try {

                        //cells == as ? 因为 && 运算符不是原子性的，需要双重检查
                        if (cells == cellsReference) {      // Expand table unless stale

                            //翻倍
                            Cell[] rs = new Cell[lenOfCells << 1];
                            for (int i = 0; i < lenOfCells; ++i) {
                                //复制
                                rs[i] = cellsReference[i];
                            }

                            //重新赋值给cells，扩容完成了
                            cells = rs;
                        }
                    } finally {
                        //释放锁
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }

                //重置当前线程hash值
                hashCodeOfCurrentThread = advanceProbe(hashCodeOfCurrentThread);
            }

            //CASE2：前置条件为cells还没有初始化，as为null

            else if (
                //条件一为true：表示当前未加锁
                    cellsBusy == 0
                            &&
                            //此处为双重检查，因为其他线程可能会在你给as赋值之后需改了cells，条件二为ture：
                            cells == cellsReference
                            &&
                            //试图获取锁
                            //条件三为true：表示获取锁成功，会把cellsBusy设置为1，false表示其他线程正在持有这把锁
                            casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    //为什么这里又要判断一次呢？防止其他线程已经初始化了，当前线程再次初始化，导致丢失数据
                    if (cells == cellsReference) {
                        //TODO 为什么是2呢？
                        Cell[] rs = new Cell[2];
                        rs[hashCodeOfCurrentThread & 1] = new Cell(addValue);
                        cells = rs;
                        init = true;
                    }
                } finally {
                    //此处释放锁
                    cellsBusy = 0;
                }
                if (init) {
                    break;
                }
            }

            //CASE3：
            //给v赋值
            //1.当前cellsBusy为加锁状态，表示其他线程正在初始化cells，所以当前线程需要把值累加到base
            //2.cells被其他线程初始化后，当前线程需要将数据累加到base
            else if (
                    casBase(v = base,
                            ((fn == null) ?//三元运算出 val
                                    v + addValue
                                    : fn.applyAsLong(v, addValue)))
            ) {
                break;                          // Fall back on using base
            }
        }
        /**** 自旋，目的是为了累加 long 值， end  ******/
    }

    /**
     * Same as longAccumulate, but injecting long/double conversions
     * in too many places to sensibly merge with long version, given
     * the low-overhead requirements of this class. So must instead be
     * maintained by copy/paste/adapt.
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn,
            boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        for (; ; ) {
            Cell[] as;
            Cell a;
            int n;
            long v;
            if ((as = cells) != null && (n = as.length) > 0) {
                if ((a = as[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs;
                                int m, j;
                                if ((rs = cells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created) {
                                break;
                            }
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                } else if (!wasUncontended)       // CAS already known to fail
                {
                    wasUncontended = true;      // Continue after rehash
                } else if (a.cas(v = a.value,
                        ((fn == null) ?
                                Double.doubleToRawLongBits
                                        (Double.longBitsToDouble(v) + x) :
                                Double.doubleToRawLongBits
                                        (fn.applyAsDouble
                                                (Double.longBitsToDouble(v), x))))) {
                    break;
                } else if (n >= NCPU || cells != as) {
                    collide = false;            // At max size or stale
                } else if (!collide) {
                    collide = true;
                } else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == as) {      // Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i) {
                                rs[i] = as[i];
                            }
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            } else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    if (cells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init) {
                    break;
                }
            } else if (casBase(v = base,
                    ((fn == null) ?
                            Double.doubleToRawLongBits
                                    (Double.longBitsToDouble(v) + x) :
                            Double.doubleToRawLongBits
                                    (fn.applyAsDouble
                                            (Double.longBitsToDouble(v), x))))) {
                break;                          // Fall back on using base
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;

    /**
     * @see Striped64#base 该变量内存偏移
     */
    private static final long BASE;

    /**
     * @see Striped64#cellsBusy 该变量内存偏移
     */
    private static final long CELLSBUSY;

    /**
     * probe:探测
     *
     * @see Thread#threadLocalRandomProbe Thread.class该变量内存偏移
     */
    private static final long PROBE;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> sk = Striped64.class;
            BASE = UNSAFE.objectFieldOffset(sk.getDeclaredField("base"));
            CELLSBUSY = UNSAFE.objectFieldOffset(sk.getDeclaredField("cellsBusy"));
            Class<?> tk = Thread.class;
            PROBE = UNSAFE.objectFieldOffset(tk.getDeclaredField("threadLocalRandomProbe"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
