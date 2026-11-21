(ns freeplane.script
  "Helper functions for Freeplane Clojure scripts, a thin wrapper over the
  org.freeplane.api interfaces. Nodes are org.freeplane.api.Node and c is an
  org.freeplane.api.Controller; every write goes through those proxies so it
  stays undoable. Collections are returned as eager vectors, functions that
  change the map end in !, and setters return the node.

  This namespace is accrete-only: functions and their error data may be added,
  never removed or changed incompatibly. A true break gets a new namespace."
  (:require [clojure.string :as str]
            [freeplane.script.fences :as fences])
  (:import (org.freeplane.api Attributes Controller FreeplaneVersion Node)
           (org.freeplane.core.util LogUtils)
           (java.util Collection)))

(set! *warn-on-reflection* true)

(defn children
  "Returns the child nodes of n as a vector."
  [^Node n]
  (vec (.getChildren n)))

(defn parent
  "Returns the parent node of n, or nil at the root."
  [^Node n]
  (.getParent n))

(defn subtree
  "Returns n and all its descendants as a vector, breadth-first."
  [^Node n]
  (vec (.findAll n)))

(defn find-nodes
  "Returns the nodes of the subtree of n, including n, for which pred is truthy."
  [^Node n pred]
  (filterv pred (subtree n)))

(defn id
  "Returns the id of node n."
  [^Node n]
  (.getId n))

(defn text
  "Returns the raw text of n, which may be plain or HTML."
  [^Node n]
  (.getText n))

(defn plain-text
  "Returns the text of n with HTML markup removed."
  [^Node n]
  (.getPlainText n))

(defn details
  "Returns the details of n as a plain-text string, or nil when absent."
  [^Node n]
  (some-> (.getDetails n) .getPlain))

(defn note
  "Returns the note of n as a plain-text string, or nil when absent."
  [^Node n]
  (some-> (.getNote n) .getPlain))

(defn attrs
  "Returns the attributes of n as an insertion-ordered {String Object} map. For a
  repeated attribute name the first value is kept, as in attr."
  [^Node n]
  (let [^Attributes attributes (.getAttributes n)
        entries (map (fn [i] [(.getKey attributes (int i)) (.get attributes (int i))])
                     (range (.size attributes)))
        first-entries (first (reduce (fn [[kept seen] [k :as entry]]
                                       (if (contains? seen k)
                                         [kept seen]
                                         [(conj kept entry) (conj seen k)]))
                                     [[] #{}]
                                     entries))]
    (apply array-map (mapcat identity first-entries))))

(defn attr
  "Returns the first value of attribute k of n, or nil."
  [^Node n k]
  (.getFirst (.getAttributes n) ^String k))

(defn node->map
  "Returns n as {:id :text :attrs :details :note :children}, with :children
  holding the maps of the child nodes, recursively."
  [^Node n]
  {:id       (id n)
   :text     (text n)
   :attrs    (attrs n)
   :details  (details n)
   :note     (note n)
   :children (mapv node->map (children n))})

(defn set-text!
  "Sets the text of n to v and returns n."
  [^Node n v]
  (.setText n v)
  n)

(defn set-details!
  "Sets the details of n to s, nil removes them. Returns n."
  [^Node n s]
  (.setDetails n s)
  n)

(defn set-note!
  "Sets the note of n to s, nil removes it. Returns n."
  [^Node n s]
  (.setNote n s)
  n)

(defn set-attr!
  "Sets attribute k of n to v; a nil v removes k. Returns n."
  [^Node n k v]
  (.putAt n ^String k v)
  n)

(defn add-child!
  "Creates a child of n with the given text and returns it. With an attrs map,
  sets the attributes on the child in map order."
  ([^Node n text]
   (.createChild n ^Object text))
  ([^Node n text attrs]
   (let [child (add-child! n text)]
     (doseq [[k v] attrs]
       (set-attr! child k v))
     child)))

(defn delete!
  "Deletes node n and returns nil."
  [^Node n]
  (.delete n))

(defn move!
  "Moves n to new-parent, at position pos when given. Returns n."
  ([^Node n ^Node new-parent]
   (.moveTo n new-parent)
   n)
  ([^Node n ^Node new-parent pos]
   (.moveTo n new-parent (int pos))
   n))

(defn selected
  "Returns the selected node of controller c."
  [^Controller c]
  (.getSelected c))

(defn selection
  "Returns the selected nodes of controller c as a vector."
  [^Controller c]
  (vec (.getSelecteds c)))

(defn select!
  "Selects a node, or a collection of nodes, and returns c. This changes only the
  UI selection: it is not undoable."
  [^Controller c node-or-nodes]
  (if (instance? Node node-or-nodes)
    (.select c ^Node node-or-nodes)
    (.select c ^Collection (vec node-or-nodes)))
  c)

(defn- joined
  ^String [xs]
  (str/join " " xs))

(defn info
  "Logs the xs, joined with spaces, at info level."
  [& xs]
  (LogUtils/info (joined xs)))

(defn warn
  "Logs the xs, joined with spaces, at warning level."
  [& xs]
  (LogUtils/warn (joined xs)))

(defn status!
  "Shows msg in the status bar of controller c and returns c."
  [^Controller c ^String msg]
  (.setStatusInfo c msg)
  c)

(defn- throw-violation
  [violation]
  (when violation (throw violation)))

(defn- freeplane-version-string
  [^FreeplaneVersion version]
  (let [release (str (.getMaj version) "." (.getMid version) "." (.getMin version))
        build (.getNum version)]
    (if (.isFinal version)
      (if (pos? build) (str release "." build) release)
      (let [type (.getType version)
            label (if (str/blank? type) "pre" type)]
        (str release "-" label (when (pos? build) build))))))

(defn require-clojure
  "Throws the :fence :requirement ex-info when this runtime's Clojure is older
  than needed, such as \"1.12\". Returns nil otherwise."
  [needed]
  (throw-violation (fences/requirement-violation :clojure (clojure-version) needed))
  nil)

(defn require-freeplane
  "Throws the :fence :requirement ex-info when the Freeplane running controller c
  is older than needed, such as \"1.13\". A non-final version, such as a release
  candidate, sorts before its release. Returns nil otherwise."
  [^Controller c needed]
  (throw-violation (fences/requirement-violation
                     :freeplane (freeplane-version-string (.getFreeplaneVersion c)) needed))
  nil)

(defn- dependency-report
  [dependency found]
  (assoc (get fences/supported dependency)
         :found found
         :newer-than-tested (fences/newer-than-tested dependency found)
         :active-quirks (fences/active-quirks fences/quirks dependency found)))

(defn compatibility
  "Returns diagnostics {:clojure {...} :freeplane {...}}. Each has :found, the
  supported :floor, :built-against and :tested-up-to, :newer-than-tested (a
  warning string or nil) and :active-quirks."
  [^Controller c]
  {:clojure   (dependency-report :clojure (clojure-version))
   :freeplane (dependency-report :freeplane (freeplane-version-string (.getFreeplaneVersion c)))})
