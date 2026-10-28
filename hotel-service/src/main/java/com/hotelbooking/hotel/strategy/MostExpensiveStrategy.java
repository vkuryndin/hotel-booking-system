package com.hotelbooking.hotel.strategy;

import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomCategory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class MostExpensiveStrategy implements RoomSelectionStrategy {

    @Override
    public List<Room> rank(List<Room> rooms) {
        return rooms.stream()
                .sorted(
                        Comparator
                                .comparingInt(
                                        (Room room) ->
                                                categoryPriority(room.getCategory())
                                )
                                .thenComparing(
                                        Room::getPricePerNight,
                                        Comparator.reverseOrder()
                                )
                                .thenComparing(Room::getId)
                )
                .toList();
    }

    private int categoryPriority(RoomCategory category) {
        return switch (category) {
            case SUITE -> 0;
            case DELUXE -> 1;
            case STANDARD -> 2;
        };
    }
}