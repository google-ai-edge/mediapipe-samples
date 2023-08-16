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


// import the necessary dependencies
import { useState, useEffect, useRef } from "react"; // React Hooks
import { connect } from "react-redux";  // Redux
import Confetti from "react-confetti";
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

import CombinedCard from "./CustComponents/CombinedCard"; // Card Component
import ModalC1 from "./CustComponents/ModalC1"; // Modal Component
import TimeComponent from "./CustComponents/TimeComponent"; // Time Component

// logout audio files
const logoutSound = "/logoutMelody.mp3";
const beepboopSound = "/beepboopSound.mp3";

// utility function to generate a string of '*' of length 'len'
// generates a masked representation of the PIN based on the length of the PIN
const pinGen = (len) => "*".repeat(len);  


// Custom Hook to handle modal interactions and keydown events
const useModalInteractions = () => {
  const [showModal, setShowModal] = useState(true);
  const [showModal2, setShowModal2] = useState(false);
  const [userInteracted, setUserInteracted] = useState(false);

  useEffect(() => { // listen for keydown events & trigger interactions
    const handleKeyDown = () => {
      if (showModal || showModal2) {
        setShowModal(false);
        setShowModal2(false);
      }
      setUserInteracted(true);
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [showModal, showModal2]);

  useEffect(() => {
    const timeout1 = setTimeout(() => {
      setShowModal(false);
      if (!userInteracted) {
        setShowModal2(true);
      }
    }, 5000);

    return () => {
      clearTimeout(timeout1);
    };
  }, [userInteracted]);

  useEffect(() => {
    if (showModal2) {
      const timeout2 = setTimeout(() => {
        setShowModal2(false);
      }, 10000);

      return () => {
        clearTimeout(timeout2);
      };
    }
  }, [showModal2]);

  const handleModalInteraction = () => {
    setUserInteracted(true);
    setShowModal(false);
    setShowModal2(false);
  };

  return { showModal, showModal2, handleModalInteraction };   // return the state and the handler
};



// Custom Hook to handle ESC keydown events
const useEscKeyRedirect = (redirectFunc, isCase3, isCase4, playAudioOnRedirect, defPageNo, resetAccountBalance, resetPin) => { 
  useEffect(() => {
    const handleKeyDown = (event) => {
      if (isCase3 && event.keyCode === 27) {
        // 'ESC' key pressed and isCase3 is true
        redirectFunc();
        resetPin();
        resetAccountBalance(550);
        toast("Logout successful!", {
          position: "top-right",
          autoClose: 2500,
          hideProgressBar: false,
          closeOnClick: true,
          pauseOnHover: true,
          draggable: true,
          progress: undefined,
          theme: "dark",
        });
        // Play the audio if specified
        if (playAudioOnRedirect) {
          const audio = new Audio(logoutSound);
          audio.play();
        }
      } else if (isCase4 && event.keyCode === 27) {
        // 'ESC' key pressed and isCase4 is true
        redirectFunc();
        defPageNo(isCase3 ? 1 : 3); // Redirect to case1 if isCase3 is true, otherwise, redirect to case3
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [redirectFunc, isCase3, isCase4, playAudioOnRedirect, defPageNo, resetAccountBalance, resetPin]);
};



function DashboardUI({ gesture }) { // Destructure the props
  const [pageNo, defPageNo] = useState(1); // Current page number
  const [pin, setPin] = useState(""); // User PIN input
  // eslint-disable-next-line 
  const [lastUndo, setlastUndo] = useState(Date.now()); // Time of the last undo (action)
  const [isProcessing, setIsProcessing] = useState(false); // Case2 to Case3 transition processing state
  const [showConfetti, setShowConfetti] = useState(false);  // Confetti state
  const [accountBalance, setAccountBalance] = useState(550); // Start with $550 (account balance)
  const [showMiniStatementModal, setShowMiniStatementModal] = useState(false);  // modal + miniStatement
  const [selectedAmount, setSelectedAmount] = useState(null); // Selected amount for withdrawal

  const toastShownRef = useRef(false);

  const showToastOnce = (msg, type) => {
    if (!toastShownRef.current) {
      toastShownRef.current = true;
      toast[type](msg, {
        position: "top-right",
        autoClose: 2500,
        hideProgressBar: false,
        closeOnClick: true,
        pauseOnHover: true,
        draggable: true,
        progress: undefined,
        theme: "dark",
        onClose: () => {
          toastShownRef.current = false; // Reset the flag after the toast is closed
        },
      });
    }
  };

  useEffect(() => {
    let backspaceGestureActive = false;

    const handleBackspaceGesture = () => {
      if (gesture === "BACKSPACE") {
        if (!backspaceGestureActive) {
          backspaceGestureActive = true;
        }
        setPin((prevPin) => prevPin.slice(0, -1));
        setlastUndo(Date.now()); // Tracking the timing of the last undo action
      } else {
        backspaceGestureActive = false;
      }
    };

    const gestureCheckInterval = setInterval(handleBackspaceGesture, 800);

    return () => {
      clearInterval(gestureCheckInterval);
    };
  }, [gesture]);


  useEffect(() => {
    const handleKeyDown = (event) => {
      if (showMiniStatementModal) {
        setShowMiniStatementModal(false);
        setShowConfetti(false);
      }
    };
  
    const handleMouseClick = (event) => {
      if (showMiniStatementModal) {
        setShowMiniStatementModal(false);
        setShowConfetti(false);
      }
    };
  
    document.addEventListener("keydown", handleKeyDown);
    document.addEventListener("click", handleMouseClick); 

    let modalTimeout;

    if (showMiniStatementModal) {
      modalTimeout = setTimeout(() => {
        setShowMiniStatementModal(false);
      }, 9300);
    }

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.removeEventListener("click", handleMouseClick);
      clearTimeout(modalTimeout);
    };
  }, [showMiniStatementModal]);


  const selectAmount = (amount) => {
    setSelectedAmount(amount);  // set the selected amount
  };

  const clearSelectedAmount = () => {
    setSelectedAmount(null);  // clear the selected amount
  };

  const withdrawCash = () => {
    if (selectedAmount === null) {
      showToastOnce("Select an amount first", "error");
    } else {
      const newBalance = accountBalance - selectedAmount;
      if (newBalance >= 0) {
        setAccountBalance(newBalance);
        showToastOnce(`Withdraw of $${selectedAmount} successful!`, "success");
      } else {
        const extraAmount = Math.abs(newBalance);
        showToastOnce(
          `Transaction Failure! Your current balance is $${accountBalance}! You'll need to add $${extraAmount} to your wallet to continue!`,
          "error"
        );
      }
    }
  };

  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.keyCode === 8 && pageNo === 2) {
        // Backspace key pressed and pageNo is 2 (case 2)
        if (pin.length > 0) {
          setPin((prevPin) => prevPin.slice(0, -1));
        } else {
          showToastOnce("It's already empty!", "warning");
          // Play the audio
          const beepboop = new Audio(beepboopSound);
          beepboop.play();
        }
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [pin, pageNo]);

  const login = () => {
    defPageNo(2);
  };

  const redirectToCase1 = () => {
    defPageNo(1);
  };

  const redirectToCase3 = () => {
    defPageNo(3);
  };

  const resetAccountBalance = (defaultBalance) => {
    setAccountBalance(defaultBalance);
  };

  const resetPin = () => {
    setPin("");
  };

  // Custom hooks for handling modal interactions and 'ESC' key redirection
  const { showModal, showModal2, handleModalInteraction } = useModalInteractions();
  useEscKeyRedirect(redirectToCase1, pageNo === 3, pageNo === 4, true, defPageNo, resetAccountBalance, resetPin);
  useEscKeyRedirect(redirectToCase3, pageNo === 4, pageNo === 3, false, defPageNo);

  // Function to handle PIN submission
  const enterPin = () => {
    if (pin === "") return showToastOnce("PIN cannot be empty!", "error");
    else if (pin.length !== 4 || pin !== "1234") return showToastOnce("Wrong PIN", "error"); // Default PIN is 1234
    else {
      setIsProcessing(true);
      setTimeout(() => {
        setIsProcessing(false);
        defPageNo(3);
      }, 2000);
    }
  };

  // Function to handle PIN input
  const addPin = (n) => {
    if (pin.length < 8) {
      setPin(pin + String(n));
    } else {
      showToastOnce("Max Input Reached", "warn");
    }
  };

  switch (pageNo) {
    case 1:
      return (
        <div
          id="panel-ui"
          className="absolute top-0 w-screen overflow-hidden h-screen flex items-center justify-center text-white p-10"
        >
          <ToastContainer />
          <ModalC1
            showModal={showModal}
            showModal2={showModal2}
            handleModalInteraction={handleModalInteraction}
          />
              <div className="absolute top-2 left-3 p-4 text-lg text-gray-400 pointer-events-none">
                <TimeComponent/>
              </div>
              <svg
                className="absolute top-3 left-64 animate-spin h-5 mt-4 text-white"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                ></circle>
                <path
                  className="opacity-75"
                  fill="white"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                ></path>
              </svg>
              <CombinedCard
                type="card4"
                onClick={login}
                className="h-40 w-80 flex flex-col items-center justify-center relative"
              >
                <img
                  src="/click_indicator.png"
                  alt=""
                  className="absolute -top-6 -right-6 w-16 h-16"
                />
                <div className="text-3xl font-medium uppercase underline font-color text-amber-300">
                  Login
                </div>
                <div className="mt-2 text-xl text-center">
                  Click here to Login 🔓
                </div>
              </CombinedCard>
              <div className="absolute bottom-1 w-screen text-center mb-4 text-gray-500 text-sm">
                Utilize the 👌 gesture using your <b>Right Hand</b> to redirect to the next screen by hovering over the <b>LOGIN</b> card.
              </div>
            </div>
          );
    case 2:
      return (
        <div
          id="panel-ui"
          className="absolute top-0 w-screen overflow-hidden h-screen flex flex-col items-center justify-center text-white p-10"
        >
          <ToastContainer />
          <div className="flex items-center -mt-10">
            <div className="text-5xl mr-6 font-medium uppercase">
              Enter your PIN
            </div>
            <div className="flex justify-center items-center mt-2 mr-4">
              <img
                src="/bounce-arrow-right.gif"
                alt="Rotated GIF"
                className="w-20 h-20 transform -rotate-90 pointer-events-none"
              />
            </div>
            <div className="bg-gray-900 rounded px-8 py-2 mt-2 ml-4 text-4xl font-mono w-64 text-center tracking-widest h-14 border-2 border-violet-400 relative">
              {pinGen(pin.length)}
              {pin.length < 8 && (
                <span className="inline-block w-4 h-1 bg-white animate-blink"></span>
              )}
            </div>
          </div>

          <br />

          <div className="grid grid-cols-4 gap-4 mt-6">
            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 0].map((number) => (
              <CombinedCard
                type="card3"
                key={number}
                className="h-20 flex items-center justify-center text-4xl"
                onClick={() => addPin(number)}
              >
                {number}
              </CombinedCard>
            ))}
            {isProcessing ? (
              <div className="h-20 col-span-2 border-indigo-500 border-4 flex items-center justify-center text-2xl font-medium rounded-xl">
                PROCESSING 
                <svg className="animate-spin h-5 ml-2 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-70" fill="blue" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
              </div>
            ) : (
              <CombinedCard
                type="card2"
                className="h-20 col-span-2 border-emerald-500 border-4 flex items-center justify-center text-2xl font-medium"
                onClick={enterPin}
              >
                ✅ SUBMIT
              </CombinedCard>
            )}

            <div className="absolute top-2 left-3 p-4 text-lg text-gray-400 pointer-events-none">
              <TimeComponent/>
            </div>
            <div className="absolute top-2 left-60 p-4 pointer-events-none">
              <img
                src="/shield_animated.gif"
                alt=""
                style={{ width: 28, height: 28 }}
              />
            </div>
          </div>

          <div className="absolute bottom-28 w-screen text-center mb-4 text-gray-500 text-lg">
            🔑 Default PIN is <b>1 2 3 4</b>
          </div>

          <div className="absolute bottom-0 w-screen text-center mb-4 text-gray-400 text-sm">
            We neither collect, store, or send any data. The video is processed
            on your browser itself & is GDPR compliant.
          </div>
        </div>
      );

    case 3:
      const services = [
        {
          title: "Withdraw Money",
          description: "Withdraw cash 💵 from your bank account.",
        },
        {
          title: "Mini Statement",
          description: "Log recent transactions of your bank account.",
        },
      ];

      return (
        <div
          id="panel-ui"
          className="absolute top-0 w-screen overflow-hidden h-screen flex flex-col items-center justify-center text-white p-10"
        >
          <ToastContainer />
          {/* Hardcoded image, update as per need :) */}
          <div className="flex">
            <div className="items-center">
              <img
                src={"/user_details.png"}
                alt=""
                className="-mt-20 mr-48 mb-12 pointer-events-none"
                style={{ width: "500px" }}
              />
            </div>
            <div className="flex flex-col">
              <div className="text-xl text-gray-400 font-mono self-end -mt-20 mr-8">
                Balance
              </div>
              <div className="text-4xl font-mono self-end mr-8 mb-12">
                ${accountBalance}
              </div>
            </div>
          </div>

          <div className="border-t border-white w-64 mb-12"></div>

          <div className="flex flex-row">
            {services.map((service, index) => (
              <CombinedCard
                type="card5"
                key={index}
                className={
                  index === 0
                    ? "h-48 w-96 mr-6 border-blue-500 border-4 flex flex-col items-center justify-center"
                    : "h-48 w-96 ml-6 border-blue-500 border-4 flex flex-col items-center justify-center"
                }
                onClick={() => {
                  if (service.title === "Mini Statement") {
                    setShowMiniStatementModal(true);
                    setShowConfetti(true);
                    showToastOnce("Success! 🎉", "success");
                  } 
                  else if (service.title === "Withdraw Money") {
                    clearSelectedAmount();
                    defPageNo(4); 
                    showToastOnce("Entering Fast Cash mode!", "warn");
                  }
                }}
              >
                <div className="uppercase underline font-bold text-2xl text-amber-500 text-center">
                  {service.title}
                </div>
                <div className="mt-2 text-center">{service.description}</div>
              </CombinedCard>
            ))}
          </div>

          <div className="absolute top-2 left-3 p-4 text-lg text-gray-400 pointer-events-none">
              <TimeComponent/>
            </div>
          <div className="absolute top-2 left-60 p-4 pointer-events-none">
            <img
              src="/shield_animated.gif"
              alt=""
              style={{ width: 28, height: 28 }}
            />
          </div>

          <div className="absolute bottom-40 flex items-center justify-center w-screen text-gray-500 text-lg">
            <span className="mr-2">Press <b>ESC</b> to logout</span>
            <img
              src="/logout_icon2.png"
              alt=""
              style={{ width: 19, height: 19 }}
            />
          </div>

          <div className="absolute bottom-0 w-screen text-center mb-4 text-gray-400 text-sm">
            We neither collect, store, or send any data. The video is processed
            on your browser itself & is GDPR compliant.
          </div>
          {showConfetti && (
            <Confetti
              recycle={false}
              style={{ pointerEvents: "none", zIndex: 40 }}
              numberOfPieces={100}
              colors={["#f44336", "#e91e63", "#9c27b0", "#673ab7", "#3f51b5", "#2196f3", "#03a9f4", "#00bcd4", "#009688", "#4CAF50", "#8BC34A",]}
              onConfettiComplete={() => setShowConfetti(false)}
            />
          )}
          {showMiniStatementModal && (
            <div className="fixed top-0 left-0 w-full h-full flex items-center justify-center z-30 bg-opacity-90 bg-black">
              <img src="./miniStatemnt.gif" alt="" className="w-3/5 h-3/5 transition-opacity duration-500 pointer-events-none z-50" />
            </div>
          )}
        </div>
      );

      case 4:
        return (
          <div
            id="panel-ui"
            className="absolute top-0 w-screen overflow-hidden h-screen flex flex-col items-center justify-center text-white p-10"
          >
            <ToastContainer />
            <div className="flex items-center -mt-24">
            <div className="text-5xl -ml-52 mr-2 font-medium uppercase">
                Select Amount
              </div>
              <div className="flex justify-center items-center mt-2 mr-4">
                <img
                  src="/bounce-arrow-right.gif"
                  alt="Rotated GIF"
                  className="w-20 h-20 -mt-2 transform pointer-events-none"
                />
              </div>
              <div className="flex flex-col">
                <div className="text-l text-gray-400 font-mono self-end mt-12 -mr-48">
                  Balance
                </div>
                <div className="text-4xl font-mono self-end -mr-48 mb-12">
                  ${accountBalance}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4 -mt-2">
              {[20, 40, 60, 80, 100, 200].map((value) => (
                <CombinedCard
                  type="card3"
                  key={value}
                  className={
                    selectedAmount === value
                      ? "h-32 flex items-center justify-center text-4xl bg-emerald-600"
                      : "h-32 flex items-center justify-center text-4xl"
                  }
                  onClick={() => {
                    if (selectedAmount === value) {
                      clearSelectedAmount();
                    } else {
                      selectAmount(value);
                    }
                  }}
                >
                  ${value}
                </CombinedCard>
              ))}
            </div>
      
            {selectedAmount !== null && (
              <div className="grid grid-cols-3 gap-4 mt-3">
                <CombinedCard
                  type="card2b"
                  className="mt-4 h-24 col-span-1 border-red-500 border-4 flex items-center justify-center text-2xl font-medium uppercase"
                  onClick={clearSelectedAmount}
                >
                  Clear Selection
                </CombinedCard>
                <CombinedCard
                  type="card2"
                  className="mt-4 h-24 col-span-2 border-emerald-500 flex items-center border-4 justify-center text-2xl font-medium uppercase"
                  onClick={withdrawCash}
                >
                  Withdraw ${selectedAmount}
                </CombinedCard>
              </div>
            )}

            <div className="absolute top-2 left-3 p-4 text-lg text-gray-400 pointer-events-none">
              <TimeComponent/>
            </div>
          <div className="absolute top-2 left-60 p-4 pointer-events-none">
            <img
              src="/shield_animated.gif"
              alt=""
              style={{ width: 28, height: 28 }}
            />
          </div>
      
            <div className="absolute bottom-24 flex items-center justify-center w-screen text-gray-500 text-lg">
              <span className="mr-2">Press <b>ESC</b> to go back to dashboard</span>
              <img
                src="/goBack_icon.png"
                alt=""
                style={{ width: 16, height: 16 }}
              />
            </div>
      
            <div className="absolute bottom-0 w-screen text-center mb-4 text-gray-400 text-sm">
              We neither collect, store, or send any data. The video is processed
              on your browser itself & is GDPR compliant.
            </div>
          </div>
        );

    default:
      return <></>;
  }
}

// Map the state to props
const PropMapFromState = (state) => ({
  gesture: state.hand.gesture,
});

// Connect the component to the Redux store
export default connect(PropMapFromState)(DashboardUI);
