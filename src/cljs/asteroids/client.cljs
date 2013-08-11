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

;; The state of the application

(def state (atom {}))

(defn init-state []
  (let [canvas (canvas)]
    (reset! state { :generation 0
                    :opacity    1
                    :context    (context canvas)
                    :x          { :coord 0 :velocity 10 :max (width  canvas)}
                    :y          { :coord 0 :velocity 0  :max (height canvas)}
                    :d          40
                    :foreground "red"
                    :background "white"
                    :history    (sorted-map) })))

;; Draw the background and the ball on the canvas


(defn setColor [context color opacity]
  (set! (.-fillStyle context) color)
  (set! (.-globalAlpha context) opacity))

(defn clear [{:keys [context x y background]}]
  (doto context
    (setColor  background 1)
    (.fillRect 0 0 (:max x) (:max y))))

(defn drawBall
    ([ {:keys [context opacity x y d foreground]}]
      (doto context
        (setColor foreground opacity)
        .beginPath 
        (.arc  (:coord x) (:coord y) d 0 (* 2 Math/PI) true)
        .closePath 
        .fill )))

(def history-size    20)
(def history-freq    10)
(def history-opacity 0.5)

(defn calculate-opacity [age]
  (/ 1 (inc (* history-opacity age))))

(defn drawHistory [state]
  (when history-size
    (doseq [[generation old-state] (:history state)]
      (let [opacity (calculate-opacity (state :generation) generation)]
        (drawBall (assoc old-state :opacity opacity))))))

(defn draw! [state]
  (doto state 
    clear
    drawHistory
    drawBall))

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


;; Store some history
;; Uses a sorted map to only store the last $history-size elements
;; Only store the history of the top-level state

(defn store-history? [state]
  (and history-size
       (zero? (mod (:generation state) history-freq))))

(defn store-history [history state]
  (conj history [ (:generation state) (dissoc state :history)]))

(defn trim-history [history]
  (if (< history-size (count history))
    (dissoc  history (first (keys history)))
    history))

(defn update-history [history state]
  (if (store-history? state)
    (-> history (store-history state) trim-history)
    history))

(defn age [state]
  (-> state
      (update-in [:history]    #(update-history % state))
      (update-in [:generation] inc)))

;; Update the state

(defn update-state [state]
  (-> state
      age
      apply-gravity
      move-ball))

;; Top level control
;; Includes functions to start/stop/restart from the repl

(def running (atom false))

(defn tick []
  (swap! state update-state)
  (draw! @state))

(defn run []
  (when @running
    (.mozRequestAnimationFrame js/window run)
    (tick)))

(defn start []
  (reset! running true)
  (run))

(defn restart []
  (stop)
  (init-state)
  (start))

(defn stop []
  (reset! running false))

(defn init []
  (restart))

;; Useful function for playing at the repl

(defn shove [x y]
  (swap! state
    (fn [s]
      (-> s
          (update-in [:x :velocity] #(+ % x))
          (update-in [:y :velocity] #(+ % y))))))

