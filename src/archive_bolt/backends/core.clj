(ns archive-bolt.backends.core
  (:require [archive-bolt.backends.s3 :as s3]))

;; Put all defmethods here to avoid circular dependencies of
;; defmulti/defmethod in multiple namespaces

(defmulti store
  "Polymorphic function for dispatching to a backend handler that should return
   the result of the operation and retry until successful or throw an error."
  (fn [backend & args] (keyword backend)))

(defmethod store :default
  [backend conf location content]
  (throw (Exception. (str "Backend not found for " backend))))

(defmethod store :s3
  [_ conf location content]
  (s3/store-content conf location content))

(defmulti filter-from-backend
  "Dispatches on backend key. Takes an optional argument for a predicate
   function to be applied when getting results.

   Returns a collection of results."
  (fn [backend & args] (keyword backend)))

(defmethod filter-from-backend :default
  [backend & args]
  (throw (Exception. (str "Backend not found for " backend))))

(defmethod filter-from-backend :s3
  [_ conf location & [pred-fn]]
  (s3/filter-from-backend conf location {:filter-fn pred-fn}))
