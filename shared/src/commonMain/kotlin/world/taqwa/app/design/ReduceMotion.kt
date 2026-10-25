package world.taqwa.app.design

/**
 * Whether the system asks for reduced motion: Android's "Remove animations" (an animator duration
 * scale of 0), iOS's Reduce Motion. Read once per screen, by the screen that reveals rows
 * ([world.taqwa.app.design.components.LocalReduceMotion]).
 */
expect fun reduceMotion(): Boolean
