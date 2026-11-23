package dev.nightbeam.corerpg.quests.client.screen.team;

/** A clickable rectangle recorded while drawing custom content; checked on mouse click. */
public record Hit(int x, int y, int w, int h, Runnable action) {
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
