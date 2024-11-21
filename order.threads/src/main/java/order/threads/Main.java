package order.threads;

import com.github.javafaker.Faker;
import order.processing.*;
import order.storage.OrderStorage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        OrderStorage<Product> storage = new OrderStorage<>();
        OrderProcessor<Product> processor = new OrderProcessor<>(storage.getStorage());
        final boolean[] allOrdersAdded = {false};

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Runnable producerTask = () -> {
                System.out.println("Start of producerTask");
                Faker faker = new Faker();
                try {
                    for (int i = 0; i < 20; i++) {
                        String productName = faker.commerce().productName();
                        String brandName = faker.company().name();
                        String clothingSize = faker.options().option("S", "M", "L", "XL");

                        System.out.println("Generated productName: " + productName);
                        System.out.println("Generated brandName: " + brandName);
                        System.out.println("Generated clothingSize: " + clothingSize);

                        Product order;
                        if (i % 2 == 0) {
                            order = Electronics.builder()
                                    .name(productName)
                                    .brand(brandName)
                                    .build();
                        } else {
                            order = Clothing.builder()
                                    .name(productName)
                                    .size(clothingSize)
                                    .build();
                        }
                        storage.addOrder(order);
                        System.out.println("Added order: " + order.getName() + "\n");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    synchronized (allOrdersAdded) {
                        allOrdersAdded[0] = true;
                        allOrdersAdded.notifyAll();
                    }
                }
                System.out.println("End of producerTask");
            };

            Runnable consumerTask = () -> {
                System.out.println("Start of consumerTask");
                while (true) {
                    synchronized (allOrdersAdded) {
                        if (allOrdersAdded[0] && storage.getStorage().isEmpty()) {
                            break;
                        }
                    }
                    processor.processOrders(order ->
                            System.out.println("Processed order: " + order.getName() + "\n")
                    );
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                System.out.println("End of consumerTask");
            };

            System.out.println("Submitting tasks to executor");
            executor.submit(producerTask);
            executor.submit(consumerTask);

            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        System.err.println("Executor did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Executor shutdown completed");
        }
    }
}