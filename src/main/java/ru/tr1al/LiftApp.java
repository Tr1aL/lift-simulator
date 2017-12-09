package ru.tr1al;

import java.util.OptionalInt;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class LiftApp {

    enum State {
        UP, DONW, STOP;
    }

    private volatile boolean work = true;

    final private static long SECOND = 1000L;
    final private static String EXIT = "exit";
    final private static Pattern INSIDE = Pattern.compile("(\\d+)i");
    final private static Pattern OUTSIDE = Pattern.compile("(\\d+)o");

    private volatile AtomicInteger currentFloor = new AtomicInteger(1);
    private volatile State lastState = State.STOP;

    private class Lift extends Thread {
        @Override
        public void run() {
            try {
                while (work) {
                    Integer nextFloor = getNextFloor();
                    if (nextFloor == null) {
                        continue;
                    }
                    moveLift(nextFloor);
                    collapseDoors();
                    shaft[currentFloor.get() - 1][0] = false;
                    shaft[currentFloor.get() - 1][1] = false;
                }
            } catch (InterruptedException e) {
                System.err.println("Лифт сломался: " + e.getMessage());
            }
        }
    }

    private void moveLift(int nextFloor) throws InterruptedException {
        if (nextFloor == currentFloor.get()) {
            return;
        }
        TimeUnit.MILLISECONDS.sleep((long) (floorHeight / speed) * SECOND);
        if (nextFloor > currentFloor.get()) {
            lastState = State.UP;
            currentFloor.incrementAndGet();
        } else {
            lastState = State.DONW;
            currentFloor.decrementAndGet();
        }
        println("лифт на этаже: " + currentFloor);
    }

    private void collapseDoors() throws InterruptedException {
        if (lastState == State.STOP ||
                lastState == State.DONW && (shaft[currentFloor.get() - 1][0] || shaft[currentFloor.get() - 1][1])
                || lastState == State.UP && (shaft[currentFloor.get() - 1][0] || currentFloor.get() == IntStream.rangeClosed(currentFloor.get(), floors).filter(f -> shaft[f - 1][1]).max().orElse(0))) {
            println("лифт открыл двери");
            TimeUnit.SECONDS.sleep(doorTime);
            println("лифт закрыл двери");
        }
    }

    private Integer getNextFloor() {
        if (lastState == State.UP) {
            //едем вверх
            //ищем есть ли с нажатой кнопкой изнутри лифта минимально больше текущего этажа
            //если нет то с нажатой кнопкой из подъезда максимально высоко
            OptionalInt next = IntStream.rangeClosed(currentFloor.get(), floors).filter(f -> shaft[f - 1][0]).min();
            if (next.isPresent()) {
                return next.getAsInt();
            }
            next = IntStream.rangeClosed(currentFloor.get(), floors).filter(f -> shaft[f - 1][1]).max();
            if (next.isPresent()) {
                return next.getAsInt();
            }
        } else if (lastState == State.DONW) {
            //едем вниз
            //ищем ближайший с нажатой кнопкой
            OptionalInt next = IntStream.rangeClosed(currentFloor.get(), floors).filter(f -> shaft[f - 1][0] || shaft[f - 1][1]).max();
            if (next.isPresent()) {
                return next.getAsInt();
            }
        }
        lastState = State.STOP;

        if (shaft[0][0]) {
            return 1;
        }
        OptionalInt next = IntStream.rangeClosed(1, floors).filter(f -> shaft[f - 1][0]).max();
        if (next.isPresent()) {
            return next.getAsInt();
        }
        if (shaft[0][1]) {
            return 1;
        }
        next = IntStream.rangeClosed(1, floors).filter(f -> shaft[f - 1][1]).max();
        if (next.isPresent()) {
            return next.getAsInt();
        }
        return null;
    }

    private class Pult extends Thread {
        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            while (work) {
                String line = scanner.nextLine();
                if (line == null || line.isEmpty()) {
                    continue;
                }
                line = line.toLowerCase().trim();
                if (line.equals(EXIT)) {
                    work = false;
                    break;
                }
                Matcher matcher = INSIDE.matcher(line);
                if (matcher.matches()) {
                    // нажать на кнопку этажа внутри лифта.
                    pressButton(Integer.parseInt(matcher.group(1)), true);
                    continue;
                }
                matcher = OUTSIDE.matcher(line);
                if (matcher.matches()) {
                    // вызов лифта на этаж из подъезда;
                    pressButton(Integer.parseInt(matcher.group(1)), false);
                    continue;
                }

                println("Неизвестная команда: " + line);
            }
        }
    }

    private void pressButton(int floor, boolean inside) {
        if (floor < 1 || floor > floors) {
            println("Неверный этаж, допустимы значения в интервале от 1 до " + floors);
            return;
        }
        shaft[floor - 1][inside ? 0 : 1] = true;
    }

    // кол-во этажей в подъезде — N (от 5 до 20);
    final private int floors;
    // высота одного этажа;
    final private double floorHeight;
    // скорость лифта при движении в метрах в секунду (ускорением пренебрегаем, считаем, что когда лифт едет — он сразу едет с определенной скоростью);
    final private double speed;
    // время между открытием и закрытием дверей.
    final private long doorTime;

    private void start() {
        printManual();
        startLift();
        startPult();
        doNothing();
    }

    private void printManual() {
        println("для завешения работы программы введите exit");
        println("для перемещения на этаж введите номер этажа и соответствующий командный символ");
        println("i - кнопка нажимается внутри лифта");
        println("o - кнопка нажимается в подъезде");
        println("например:");
        println("8i - 8 этаж, кнопка нажата в лифте");
        println("11o - 11 этаж, кнопка нажата в подъезде");
    }

    private void doNothing() {
        while (work) {
            //do nothing
        }
    }

    private void startLift() {
        Lift lift = new Lift();
        lift.start();
    }

    private void startPult() {
        Pult pult = new Pult();
        pult.start();
    }

    private boolean shaft[][];

    public LiftApp(int floors, double floorHeight, double speed, long doorTime) {
        if (floors < 5) {
            floors = 5;
        }
        if (floors > 20) {
            floors = 20;
        }
        this.floors = floors;
        this.floorHeight = floorHeight;
        this.speed = speed;
        this.doorTime = doorTime;
        this.shaft = new boolean[floors][2];
    }

    private static void println(String str) {
        System.out.println(str);
    }

    public static void main(String[] args) {
        int floors;
        double floorHeight;
        double speed;
        int doorTime;
        if (args.length != 4) {
            System.err.println("Неверные входные данные");
            return;
        }
        try {
            floors = Integer.parseInt(args[0]);
            floorHeight = Double.parseDouble(args[1]);
            speed = Double.parseDouble(args[2]);
            doorTime = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            System.err.println("Неверные входные данные");
            return;
        }
        if (floors < 5 || floors > 20) {
            System.err.println("Кол-во этажей должно быть от 5 до 20");
            return;
        }
        new LiftApp(floors, floorHeight, speed, doorTime).start();
    }
}
