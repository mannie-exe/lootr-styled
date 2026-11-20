import Base
import ./MODEL.bend as Model

law disabled_native:
  for c: Bool
  for e: Bool
  for d: Bool
  for i: Bool
  {Model.source(False{}, c, e, d, i) == 0n : Nat}

law selected_wins:
  for e: Bool
  for d: Bool
  for i: Bool
  {Model.source(True{}, True{}, e, d, i) == 3n : Nat}

law explicit_above_default_and_inheritance:
  for d: Bool
  for i: Bool
  {Model.source(True{}, False{}, True{}, d, i) == 2n : Nat}

law default_above_inherited:
  for i: Bool
  {Model.source(True{}, False{}, False{}, True{}, i) == 4n : Nat}

law missing_slot_inherits:
  {Model.source(True{}, False{}, False{}, False{}, True{}) == 1n : Nat}

law requested_identity_survives:
  for request: Nat
  for effective: Nat
  {Model.requested(Model.resolve(request, effective)) == request : Nat}

law exhausted_inputs_refused:
  for graph: Bool
  for outputs: Bool
  {Model.complete(False{}, graph, outputs) == False{} : Bool}

law failed_graph_refused:
  for outputs: Bool
  {Model.complete(True{}, False{}, outputs) == False{} : Bool}

law exhausted_output_refused:
  {Model.complete(True{}, True{}, False{}) == False{} : Bool}

law incomplete_generation_refused:
  for current: Nat
  for candidate: Nat
  {Model.publish(current, candidate, False{}) == False{} : Bool}

law stale_generation_refused:
  for +current: Nat
  for +candidate: Nat
  for complete: Bool
  for stale: {Nat.is_eq(current, candidate) == False{} : Bool}
  {Model.publish(current, candidate, complete) == False{} : Bool}

law preview_not_packaged:
  for own: Bool
  for foreign: Bool
  {Model.package(own, True{}, foreign) == False{} : Bool}

law foreign_not_packaged:
  for own: Bool
  {Model.package(own, False{}, True{}) == False{} : Bool}

law fresh_off:
  for capable: Bool
  for source: Bool
  {Model.fresh(True{}, capable, source) == False{} : Bool}

law unknown_fresh_not_success:
  {Model.fresh(False{}, False{}, True{}) == False{} : Bool}

law default_artwork_empty:
  {Model.default_artwork() == 0n : Nat}
