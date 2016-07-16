#ifndef VSF_MCUPDATE_H
#define VSF_MCUPDATE_H

/* mcu_get_latest_version()
 * PURPOSE
 * Get the latest version of a specified modpack.
 *
 * PARAMETERS
 * modpack      - the modpack for wich version info is requested
 * version      - the version string
 *
 * RETURNS
 * An integer representing the success/failure of opening the version file.
 * Nonzero indicates success.
 */
int mcu_get_latest_version(const struct mystr* modpack, struct mystr* version);


int mcu_create_update_instructions(struct mystr* instr, const struct mystr* modpack, const struct mystr* version);
#endif /* VSF_MCUPDATE_H */
