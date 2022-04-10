package net.emittman;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class WordCountTest {
    private WordCount wordCount;
    private Map<String, Integer> expectations;

    @Before
    public void setUp() {
        expectations = new HashMap<>();
        expectations.put("one", 1);
        expectations.put("two", 2);
        expectations.put("three", 3);

        wordCount = new WordCount();
        for(Map.Entry<String,Integer> entry: expectations.entrySet()) {
            wordCount.getWordCounts().put(entry.getKey(), entry.getValue());
        }
    }

    private void verify() {
        Map<String,Integer> wordCounts = wordCount.getWordCounts();
        assertTrue(wordCounts instanceof ConcurrentSkipListMap);
        assertEquals(expectations.size(), wordCounts.size());
        for(Map.Entry<String,Integer> entry: expectations.entrySet()) {
            String k = entry.getKey();
            assertEquals(expectations.get(k), wordCount.getWordCounts().get(k));
        }
    }

    @Test
    public void testAddNewWordAddsForMissing() {
        String word = "missing";
        Integer value = 1;
        wordCount.addWord(word);
        expectations.put(word, value);
        verify();
    }

    @Test
    public void testAddWordIncrementsForExisting() {
        String word = "one";
        Integer value = 2;
        wordCount.addWord(word);
        expectations.put(word, value);
        verify();
    }

    @Test
    public void testRemoveWordDecrementsForExisting() {
        String word = "three";
        Integer value = 2;
        wordCount.removeWord(word);
        expectations.put(word, value);
        verify();
    }

    @Test
    public void testRemoveWordRemovesForNoLongerExisting() {
        String word = "one";
        wordCount.removeWord(word);
        expectations.remove(word);
        verify();
    }

    @Test
    public void testRemoveWordDoesNothingForMissing() {
        String word = "missing";
        wordCount.removeWord(word);
        verify();
    }

    @Ignore
    @Test
    public void testClassIsMtSafe() throws InterruptedException {
        final Consumer<String> ADD = (w) -> wordCount.addWord(w);
        final Consumer<String> REMOVE = (w) -> wordCount.removeWord(w);

        class Worker implements Runnable {
            final String word;
            final Consumer<String> action;

            Worker(String word, final Consumer<String> action) {
                this.word = word;
                this.action = action;
            }
            @Override
            public void run() {
                action.accept(word);
            }
        }

        class PauseableThreadPoolExecutor extends ThreadPoolExecutor {
            private boolean isPaused;
            private ReentrantLock pauseLock = new ReentrantLock();
            private Condition unpaused = pauseLock.newCondition();

            PauseableThreadPoolExecutor(int numWorkers, BlockingQueue<Runnable> workQueue) {
                super(numWorkers, numWorkers, 2, TimeUnit.SECONDS, workQueue);
            }

            protected void beforeExecute(Thread t, Runnable r) {
                super.beforeExecute(t, r);
                pauseLock.lock();
                try {
                    while (isPaused) unpaused.await();
                } catch (InterruptedException ie) {
                    t.interrupt();
                } finally {
                    pauseLock.unlock();
                }
            }

            public void pause() {
                pauseLock.lock();
                try {
                    isPaused = true;
                } finally {
                    pauseLock.unlock();
                }
            }

            public void resume() {
                pauseLock.lock();
                try {
                    isPaused = false;
                    unpaused.signalAll();
                } finally {
                    pauseLock.unlock();
                }
            }
        }

        final int NUM_WORDS = 2;
        final int NUM_WORKERS = 2;   // NUM_WORDS * NUM_WORDS * 2 + 1;
        expectations.clear();
        wordCount.getWordCounts().clear();
        // Net changes of one more occurrence
        // for the string representation of each number
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(NUM_WORKERS);
        PauseableThreadPoolExecutor executor = new PauseableThreadPoolExecutor(NUM_WORKERS, workQueue);
        executor.pause();

        Random random = new Random();
        for (int i=1; i<=NUM_WORDS; ++i) {
            final String word = Integer.toString(i);
            expectations.put(word, i + 1);
            wordCount.getWordCounts().put(word, i);
            executor.execute(new Worker(word, ADD));
            for(int j=0; j<i; ++j) {
                if (random.nextBoolean()) {
                    executor.execute(new Worker(word, REMOVE));
                    executor.execute(new Worker(word, ADD));
                }
                else {
                    executor.execute(new Worker(word, ADD));
                    executor.execute(new Worker(word, REMOVE));
                }
            }
        }
        executor.resume();
        executor.shutdown();
        if (executor.awaitTermination(5, TimeUnit.SECONDS)) {
            verify();
        }
        else {
            fail("The executor was not able to execute all of the tasks in the alloted time");
        }
    }
}

