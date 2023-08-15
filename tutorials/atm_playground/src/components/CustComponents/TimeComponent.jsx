// Copyright 2023 The MediaPipe Authors.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

//      http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// ---------------------------------------------------------------------------------------- //


// Import necessary modules from the React library
import React, { useState, useEffect } from "react";

function TimeComponent() {
  // `useState` hook is used to initialize state variables for time, day name, month, and day
  const [time, setTime] = useState(getFormattedTime());
  const [dayname, setDayName] = useState("");
  const [month, setMonth] = useState("");
  const [day, setDay] = useState("");

  // We use `useEffect` to update the time every second.
  useEffect(() => {
    const intervalId = setInterval(() => {
      const formattedTime = getFormattedTime();
      setTime(formattedTime);
    }, 1000);

    // We use `clearInterval` to clear the interval when the component is unmounted.
    return () => clearInterval(intervalId);
  }, []);

  // We use `useEffect` to initialize the day name, month, and day when the component is mounted.
  useEffect(() => {
    const today = new Date();
    const dayIndex = today.getDay();
    const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    const monthNames = [
      "Jan", "Feb", "Mar", "Apr", "May", "Jun",
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    ];

    setDayName(days[dayIndex]);
    setMonth(monthNames[today.getMonth()]);
    setDay(String(today.getDate()).padStart(2, "0"));
  }, []);

  // This function returns the current time in the format "hh:mm:ss".
  function getFormattedTime() {
    const currentTime = new Date();
    return currentTime.toLocaleTimeString(navigator.language, {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  }

  return (
    <div className="App">
      <h1>
        {time} • {dayname}, {month} {day}
      </h1>
    </div>
  );
}

// Export the `TimeComponent` component
export default TimeComponent;
