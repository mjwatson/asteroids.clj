(ns asteroids.client
  (:require [clojure.browser.repl]))

;; Simple bouncing ball demo to get a feel for canvas and the repl
;; ***************************************************************

;; Global setting

(def gravity 0.1)

(def elasticity 0.80)

;; The canvas

(defn canvas []
  (-> js/document (.getElementById "canvas")))

(defn context [canvas]
  (.getContext canvas "2d"))

(defn width [canvas]
  (.-width canvas))

(defn height [canvas]
  (.-height canvas))

;; The current state of the application
;; Stored in an atom for access via the repl

(defn initial-state []
  (let [canvas (canvas)]
    {:generation 0
     :opacity    1
     :context    (context canvas)
     :x          { :coord 0 :velocity 10 :max (width  canvas)}
     :y          { :coord 0 :velocity 0  :max (height canvas)}
     :d          40
     :foreground "red"
     :background "white" }))


;; History parameters controlling how many previous posistions are displayed

(def history-size    10)
(def history-freq    10)
(def history-queue   (* history-size history-freq))
(def history-opacity 0.5)

;; Draw the background and the ball on the canvas
;; Old states are drawn with increasing transparency

(defn setColor [context color opacity]
  (set! (.-fillStyle context) color)
  (set! (.-globalAlpha context) opacity))

(defn clear [{:keys [context x y background]}]
  (doto context
    (setColor  background 1)
    (.fillRect 0 0 (:max x) (:max y))))

(defn draw-ball
    ([ {:keys [context opacity x y d foreground]}]
      (doto context
        (setColor foreground opacity)
        .beginPath 
        (.arc  (:coord x) (:coord y) d 0 (* 2 Math/PI) true)
        .closePath 
        .fill )))

(defn calculate-opacity [age]
  (/ 1 (inc (* history-opacity age))))

(defn draw-history [state history]
  (when history-size
    (doseq [old-state (take-nth history-freq  history) :when (not (nil? old-state))]
      (let [opacity (calculate-opacity (- (state :generation) (old-state :generation)))]
        (draw-ball (assoc old-state :opacity opacity))))))

(defn draw! [history]
  (let [state (last history)]
    (doto state
      clear 
      (draw-history history)
      draw-ball)))

;; Move the ball

(defn bounce [ {:keys [coord velocity max] :as dimension } ]
  (let [new-coord (+ coord velocity)]
    (if (and (< 0 new-coord) (< new-coord max))
      (assoc dimension :coord    new-coord)
      (assoc dimension :velocity (* -1 elasticity velocity)))))

(defn move-ball [state]
  (-> state
      (update-in [:x] bounce)
      (update-in [:y] bounce)))

(defn apply-gravity [state]
  (update-in state [:y :velocity] #(+ % gravity)))

(defn age [state]
  (update-in state [:generation] inc))

(defn update-state [state]
  (-> state
      age
      apply-gravity
      move-ball))

;; Create an (almost) purely functional history as a sequence of the
;; last $history-size elements.
;;
;; Its not quite functional, as rather than just using iterate,
;; we store the last value of the state is stored in an atom
;; This allows it to be modified and viewed easily from the repl.
;; If this wasn't wanted then next-state could be just update-state.

(def state  (atom nil))
(def stream (atom nil))

(defn store-initial-state []
  (reset! state (initial-state)))

(defn next-state [& args]
  (swap! state update-state))

(defn empty-history []
  (repeat history-queue nil))

(defn state-stream []
  (iterate next-state (store-initial-state)))

(defn history-stream []
  (partition history-queue 1 (concat (empty-history) (state-stream))))

(defn init-state []
  (reset! stream (history-stream)))

;; Top level control
;; Includes functions to start/stop/restart from the repl

(def running (atom false))

(defn tick []
  (swap! stream rest)
  (draw! (first @stream)))

(defn run []
  (when @running
    (.mozRequestAnimationFrame js/window run)
    (tick)))

(defn start []
  (reset! running true)
  (run))

(defn stop []
  (reset! running false))

(defn restart []
  (stop)
  (init-state)
  (start))

(defn init []
  (restart))

;; Useful function for playing at the repl

(defn shove [x y]
  (swap! state
         (fn [s]
           (-> s
               (update-in [:x :velocity] #(+ % x))
               (update-in [:y :velocity] #(+ % y))))))

