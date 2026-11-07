package me.petr1furious.potatominigames.games.party;
record PartyRoundOptions(boolean embedded,int countdownSeconds,int armsRaceSeconds,int minersPreparationSeconds,int minersCombatSeconds,int minersFinaleSeconds) {
    static PartyRoundOptions party() { return new PartyRoundOptions(true,5,180,60,90,30); }
    static PartyRoundOptions standalone() { return new PartyRoundOptions(false,5,600,120,180,60); }
}
