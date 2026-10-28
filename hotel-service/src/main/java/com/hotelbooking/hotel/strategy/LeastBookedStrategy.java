package com.hotelbooking.hotel.strategy;

import com.hotelbooking.hotel.entity.Room;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class LeastBookedStrategy implements RoomSelectionStrategy {

    @Override
    public List<Room> rank(List<Room> rooms) {
        return rooms.stream()
                .sorted(
                        Comparator.comparingLong(Room::getTimesBooked)
                                .thenComparing(Room::getId)
                )
                .toList();
    }
}