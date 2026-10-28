package com.hotelbooking.hotel.strategy;

import com.hotelbooking.hotel.entity.Room;
import java.util.List;

public interface RoomSelectionStrategy {
    List<Room> rank(List<Room> rooms);
}