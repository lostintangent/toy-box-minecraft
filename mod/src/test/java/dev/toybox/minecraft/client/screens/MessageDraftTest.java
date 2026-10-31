package dev.toybox.minecraft.client.screens;

/** The owner of a draft and pending request outlives any particular transcript screen. */
public final class MessageDraftTest {
  public static void main(String[] args) {
    MessageDraft draft = new MessageDraft();
    draft.edit("A message being composed");
    var pending = draft.begin();
    require(draft.sending() && draft.begin() == null, "a pending draft cannot submit twice");
    draft.edit("A newer unsent message");
    require(
        !draft.complete(pending, true, null) && draft.value().equals("A newer unsent message"),
        "old receipt cannot erase newer draft");

    var exact = draft.begin();
    require(
        draft.complete(exact, true, null) && draft.value().isEmpty(),
        "matching successful receipt clears shared draft");
    draft.edit("Keep on failure");
    var failed = draft.begin();
    require(
        !draft.complete(failed, false, "Unconfirmed")
            && draft.value().equals("Keep on failure")
            && !draft.sending(),
        "failure retains editable draft without retry");
    var retry = draft.begin();
    require(
        !draft.complete(failed, true, null) && draft.sending(),
        "obsolete completion cannot alter current request");
    draft.complete(retry, false, "Unconfirmed");

    draft.prefill("");
    require(draft.value().equals("Keep on failure"), "blank reply navigation preserves draft");
    draft.prefill("@teammate ");
    require(
        draft.value().equals("@teammate Keep on failure"),
        "context prefixes retain all existing text");
    draft.prefill("@teammate ");
    require(
        draft.value().equals("@teammate Keep on failure"),
        "reopening same contextual composer does not duplicate prefix");
    var edited = draft.begin();
    String submitted = draft.value();
    draft.edit("Temporary edit");
    draft.edit(submitted);
    require(
        !draft.complete(edited, true, null) && draft.value().equals(submitted),
        "an intentional later edit to the same text is not silently cleared");
    draft.edit("x".repeat(12_000));
    draft.prefill("About this task: ");
    require(
        draft.value().length() == 12_000 && draft.value().startsWith("x"),
        "prefill cannot truncate a full existing draft");
    System.out.println(
        "Toy Box composer: draft navigation, pending send and receipt contracts passed");
  }

  private static void require(boolean value, String contract) {
    if (!value) throw new AssertionError(contract);
  }
}
