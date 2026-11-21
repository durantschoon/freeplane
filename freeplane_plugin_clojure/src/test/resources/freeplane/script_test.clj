(ns freeplane.script-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [freeplane.script :as fp])
  (:import (org.freeplane.api Attributes Controller Convertible FreeplaneVersion Node)
           (java.util Collection)))

(declare node-stub)

(defn- convertible
  [plain]
  (reify Convertible (getPlain [_] plain)))

(defn- attributes-stub
  [pairs]
  (reify Attributes
    (size [_] (count pairs))
    (getKey [_ i] (first (nth pairs i)))
    (^Object get [_ ^int i] (second (nth pairs i)))
    (getFirst [_ k] (some (fn [[pk v]] (when (= pk k) v)) pairs))))

(defn- node-stub
  "A Node stub. Options: :id :text :plain :attrs (vector of pairs) :details :note
  :calls (atom collecting the write calls). Returns the stub and its link atoms."
  [{:keys [id text plain attrs details note calls]
    :or {attrs [] calls (atom [])}}]
  (let [parent-atom (atom nil)
        children-atom (atom [])
        stub (reify Node
               (getId [_] id)
               (getText [_] text)
               (getPlainText [_] (or plain text))
               (getAttributes [_] (attributes-stub attrs))
               (getDetails [_] (when details (convertible details)))
               (getNote [_] (when note (convertible note)))
               (getParent [_] @parent-atom)
               (getChildren [_] @children-atom)
               (findAll [this]
                 (loop [queue [this] out []]
                   (if (empty? queue)
                     out
                     (recur (into (subvec queue 1) (.getChildren ^Node (first queue)))
                            (conj out (first queue))))))
               (setText [_ v] (swap! calls conj [:setText id v]))
               (setDetails [_ v] (swap! calls conj [:setDetails id v]))
               (setNote [_ v] (swap! calls conj [:setNote id v]))
               (putAt [_ k v] (swap! calls conj [:putAt id k v]) v)
               (^Node createChild [_ ^Object v]
                 (swap! calls conj [:createChild id v])
                 (:stub (node-stub {:id (str id "/new") :text (str v) :calls calls})))
               (delete [_] (swap! calls conj [:delete id]))
               (moveTo [_ p] (swap! calls conj [:moveTo id (.getId ^Node p)]))
               (moveTo [_ p pos] (swap! calls conj [:moveTo id (.getId ^Node p) pos])))]
    {:stub stub :parent parent-atom :children children-atom :calls calls}))

(defn- link!
  [parent-node child-node]
  (swap! (:children parent-node) conj (:stub child-node))
  (reset! (:parent child-node) (:stub parent-node)))

(defn- build-tree
  "root -> a (grandchild a1), b"
  []
  (let [calls (atom [])
        root (node-stub {:id "R" :text "root" :calls calls
                         :attrs [["k" "1"] ["z" "2"] ["k" "3"]]
                         :details "det" :note "nt"})
        a (node-stub {:id "A" :text "<html>a</html>" :plain "a" :calls calls})
        b (node-stub {:id "B" :text "b" :calls calls})
        a1 (node-stub {:id "A1" :text "a1" :calls calls :attrs [["x" "y"]]})]
    (link! root a) (link! root b) (link! a a1)
    {:root root :a a :b b :a1 a1 :calls calls}))

(deftest read-functions-test
  (let [{:keys [root a b a1]} (build-tree)
        r (:stub root) na (:stub a) nb (:stub b) n1 (:stub a1)]
    (is (= [na nb] (fp/children r)))
    (is (vector? (fp/children r)))
    (is (nil? (fp/parent r)))
    (is (identical? r (fp/parent na)))
    (is (= [r na nb n1] (fp/subtree r)))
    (is (vector? (fp/subtree r)))
    (is (= [na n1] (fp/find-nodes r #(str/starts-with? (fp/id %) "A"))))
    (is (= "R" (fp/id r)))
    (is (= "<html>a</html>" (fp/text na)))
    (is (= "a" (fp/plain-text na)))))

(deftest attrs-test
  (let [{:keys [root b]} (build-tree)
        r (:stub root)]
    (is (= ["k" "z"] (keys (fp/attrs r))))
    (is (= {"k" "1" "z" "2"} (fp/attrs r)))
    (is (= "1" (fp/attr r "k")))
    (is (nil? (fp/attr r "missing")))
    (is (= {} (fp/attrs (:stub b))))
    (let [many (node-stub {:id "M" :text "m"
                           :attrs (mapv (fn [i] [(str "a" i) (str i)]) (range 12))})]
      (is (= (mapv #(str "a" %) (range 12)) (vec (keys (fp/attrs (:stub many)))))))))

(deftest details-and-note-test
  (let [{:keys [root a]} (build-tree)]
    (is (= "det" (fp/details (:stub root))))
    (is (= "nt" (fp/note (:stub root))))
    (is (nil? (fp/details (:stub a))))
    (is (nil? (fp/note (:stub a))))))

(deftest node->map-test
  (let [{:keys [root]} (build-tree)]
    (is (= {:id "R" :text "root" :attrs {"k" "1" "z" "2"} :details "det" :note "nt"
            :children [{:id "A" :text "<html>a</html>" :attrs {} :details nil :note nil
                        :children [{:id "A1" :text "a1" :attrs {"x" "y"} :details nil
                                    :note nil :children []}]}
                       {:id "B" :text "b" :attrs {} :details nil :note nil :children []}]}
           (fp/node->map (:stub root))))))

(deftest setters-test
  (let [{:keys [root calls]} (build-tree)
        r (:stub root)]
    (is (identical? r (fp/set-text! r "new")))
    (is (identical? r (fp/set-details! r "d")))
    (is (identical? r (fp/set-note! r "n")))
    (is (identical? r (fp/set-attr! r "k" "v")))
    (is (identical? r (fp/set-attr! r "gone" nil)))
    (is (identical? r (fp/set-details! r nil)))
    (is (= [[:setText "R" "new"] [:setDetails "R" "d"] [:setNote "R" "n"]
            [:putAt "R" "k" "v"] [:putAt "R" "gone" nil] [:setDetails "R" nil]]
           @calls))))

(deftest add-child-test
  (let [{:keys [root calls]} (build-tree)
        r (:stub root)
        plain-child (fp/add-child! r "one")
        child (fp/add-child! r "two" (array-map "b" "2" "a" "1"))]
    (is (= "R/new" (.getId ^Node plain-child)))
    (is (= "R/new" (.getId ^Node child)))
    (is (= [[:createChild "R" "one"] [:createChild "R" "two"]
            [:putAt "R/new" "b" "2"] [:putAt "R/new" "a" "1"]]
           @calls))))

(deftest delete-and-move-test
  (let [{:keys [a b calls]} (build-tree)
        na (:stub a) nb (:stub b)]
    (fp/delete! na)
    (is (identical? nb (fp/move! nb na)))
    (is (identical? nb (fp/move! nb na 2)))
    (is (= [[:delete "A"] [:moveTo "B" "A"] [:moveTo "B" "A" 2]] @calls))))

(deftest controller-test
  (let [calls (atom [])
        {:keys [root a]} (build-tree)
        r (:stub root) na (:stub a)
        c (reify Controller
            (getSelected [_] r)
            (getSelecteds [_] (java.util.ArrayList. ^Collection [r na]))
            (^void select [_ ^Node n] (swap! calls conj [:select n]))
            (^void select [_ ^Collection ns] (swap! calls conj [:select-many (vec ns)]))
            (^void setStatusInfo [_ ^String s] (swap! calls conj [:status s])))]
    (is (identical? r (fp/selected c)))
    (is (= [r na] (fp/selection c)))
    (is (vector? (fp/selection c)))
    (fp/select! c r)
    (fp/select! c [r na])
    (fp/select! c (list na))
    (fp/status! c "hello")
    (is (= [[:select r] [:select-many [r na]] [:select-many [na]] [:status "hello"]] @calls))))

(defn- version-stub
  [maj mid min final? type num]
  (reify FreeplaneVersion
    (getMaj [_] maj) (getMid [_] mid) (getMin [_] min)
    (isFinal [_] final?) (getType [_] type) (getNum [_] num)))

(defn- controller-with
  [version]
  (reify Controller (getFreeplaneVersion [_] version)))

(defn- violation-data
  [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e (ex-data e))))

(deftest require-clojure-test
  (is (nil? (fp/require-clojure "1.12")))
  (is (= :requirement (:fence (violation-data #(fp/require-clojure "99.0")))))
  (is (= :clojure (:dependency (violation-data #(fp/require-clojure "99.0"))))))

(deftest require-freeplane-test
  (let [final (controller-with (version-stub 1 13 4 true "" 0))
        rc (controller-with (version-stub 1 13 4 false "rc" 0))
        old (controller-with (version-stub 1 12 18 true "" 0))]
    (is (nil? (fp/require-freeplane final "1.13.4")))
    (is (= :requirement (:fence (violation-data #(fp/require-freeplane rc "1.13.4")))))
    (is (nil? (fp/require-freeplane rc "1.13.3")))
    (is (= :requirement (:fence (violation-data #(fp/require-freeplane old "1.13")))))))

(deftest compatibility-test
  (let [report (fp/compatibility (controller-with (version-stub 1 13 4 true "" 0)))]
    (is (= #{:clojure :freeplane} (set (keys report))))
    (is (= (clojure-version) (get-in report [:clojure :found])))
    (is (= "1.13.4" (get-in report [:freeplane :found])))
    (is (= "1.13.0" (get-in report [:freeplane :floor])))
    (is (nil? (get-in report [:freeplane :newer-than-tested])))
    (is (= [] (get-in report [:freeplane :active-quirks])))
    (is (contains? (:clojure report) :tested-up-to))))

(deftest no-reflection-warnings-test
  (let [err (java.io.StringWriter.)]
    (binding [*err* err
              *warn-on-reflection* true]
      (require 'freeplane.script :reload))
    (is (not (str/includes? (str err) "eflection")) (str err))))

(deftest source-fences-test
  (let [source (slurp (io/resource "freeplane/script.clj"))]
    (is (not (str/includes? source "getDelegate")))
    (is (not (str/includes? source "org.freeplane.features")))))

(deftest public-api-hygiene-test
  (let [mine (ns-publics 'freeplane.script)
        core (ns-publics 'clojure.core)]
    (is (seq mine))
    (testing "no clojure.core collisions"
      (is (empty? (filter #(contains? core %) (keys mine)))))
    (testing "docstrings"
      (is (empty? (remove #(:doc (meta (val %))) mine))
          (str (remove #(:doc (meta (val %))) mine))))))
