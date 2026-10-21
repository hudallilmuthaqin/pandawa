package id.pbpandawa.match;

import id.pbpandawa.player.Player;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** Participation of one player in one match (team 1 or 2, slot 1 or 2). */
@Entity
@Table(name = "match_players")
@IdClass(MatchPlayer.Key.class)
public class MatchPlayer {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @Id
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(nullable = false)
    private short team;

    @Column(nullable = false)
    private short slot;

    protected MatchPlayer() {
    }

    MatchPlayer(Match match, Player player, short team, short slot) {
        this.match = match;
        this.player = player;
        this.team = team;
        this.slot = slot;
    }

    public Match getMatch() { return match; }
    public Player getPlayer() { return player; }
    public short getTeam() { return team; }
    public short getSlot() { return slot; }

    public static class Key implements Serializable {
        private Long match;
        private Long player;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(k.match, match) && Objects.equals(k.player, player);
        }

        @Override
        public int hashCode() {
            return Objects.hash(match, player);
        }
    }
}
