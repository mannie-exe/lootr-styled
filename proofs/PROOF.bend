import Base
import ./MODEL.bend as Model
import ./LAWS.bend as Laws

def Laws.disabled_native(c, e, d, i):
  {==}

def Laws.selected_wins(e, d, i):
  {==}

def Laws.explicit_above_default_and_inheritance(d, i):
  {==}

def Laws.default_above_inherited(i):
  {==}

def Laws.missing_slot_inherits():
  {==}

def Laws.requested_identity_survives(request, effective):
  {==}

def Laws.exhausted_inputs_refused(graph, outputs):
  {==}

def Laws.failed_graph_refused(outputs):
  {==}

def Laws.exhausted_output_refused():
  {==}

def and_false_right(b: Bool) -> {Bool.and(b, False{}) == False{} : Bool}:
  match b:
    case False{}:
      {==}
    case True{}:
      {==}

def Laws.incomplete_generation_refused(current, candidate):
  and_false_right(Nat.is_eq(current, candidate))

def Laws.stale_generation_refused(current, candidate, complete, stale):
  Equal.cong(Bool, Bool, same => Bool.and(same, complete), Nat.is_eq(current, candidate), False{}, stale)

def Laws.preview_not_packaged(own, foreign):
  match own:
    case False{}:
      {==}
    case True{}:
      {==}

def Laws.foreign_not_packaged(own):
  match own:
    case False{}:
      {==}
    case True{}:
      {==}

def Laws.fresh_off(capable, source):
  {==}

def Laws.unknown_fresh_not_success():
  {==}

def Laws.default_artwork_empty():
  {==}
