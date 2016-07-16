/*
 * Part of Very Secure FTPd
 * Licence: GPL v2
 * Author: Dark Ayron
 * mcupdate.c
 *
 * Routines for generating update instructions.
 * This is the minecraft updater addon.
 */

#include "tunables.h"
#include "str.h"
#include "filestr.h"
#include "defs.h"
#include "sysutil.h"
#include "sysstr.h"
#include "utility.h"
#include "ftpcmdio.h"

int
mcu_get_latest_version(const struct mystr* modpack, struct mystr* version)
{
  struct mystr filename = INIT_MYSTR;
  int result;
  str_alloc_text(&filename, "/DATA/VERSION/");
  str_append_str(&filename, modpack);

  result = str_fileread(version, str_getbuf(&filename), 40);
  str_free(&filename);
  str_replace_text(version, "\n", "");
  return (result >= 0);
}

int
mcu_create_update_instructions(struct mystr* instr, const struct mystr* modpack, const struct mystr* version)
{
  static struct mystr filename;
  static struct mystr list1;
  static struct mystr list2;
  static struct mystr file;
  static struct mystr file2;
  static struct mystr filep;
  static struct mystr filem;
  static struct mystr filea;
  static struct mystr version_latest;
  static struct mystr version_line;
  struct vsf_sysutil_statbuf* statbuf = 0;
  int result;
  int fd;
  unsigned int pos;
  char c;
  result = mcu_get_latest_version(modpack, &version_latest);
  if (!result)
  {
    return 1;
  }
  /* read the listfile of the old version */
  str_alloc_text(&filename, "/DATA/LISTFILE/");
  str_append_str(&filename, modpack);
  str_append_text(&filename, "/");
  str_append_str(&filename, version);
  result = str_fileread(&list1, str_getbuf(&filename), 100000);
  if (result < 0)
  {
    return 2;
  }
  /* read the most recent listfile */
  str_alloc_text(&filename, "/DATA/LISTFILE/");
  str_append_str(&filename, modpack);
  str_append_text(&filename, "/");
  str_append_str(&filename, &version_latest);
  result = str_fileread(&list2, str_getbuf(&filename), 100000);
  if (result < 0)
  {
    return -1;
  }
  str_empty(instr);
  pos = 0;
  do
  {
    result = str_getline(&list1, &file, &pos);
    if (str_equal_text(&file, ".") || str_isempty(&file) || str_contains_line(&list2, &file))
    {
      continue;
    }
    else
    {
      str_append_char(&file, '\n');
      str_append_str(instr, &file);
    }
  } while(result);
  str_replace_text(instr, "./", "-umods/");
  pos = 0;
  do
  {
    result = str_getline(&list2, &file, &pos);
    if (str_equal_text(&file, ".") || str_isempty(&file) || str_contains_line(&list1, &file))
    {
      continue;
    }
    else
    {
      str_alloc_text(&file2, "/");
      str_append_str(&file2, modpack);
      str_append_text(&file2, "/mods/");
      str_append_str(&file2, &file);
      fd = str_open(&file2, kVSFSysStrOpenReadOnly);
      if (str_stat(&file2, &statbuf))
      {
        str_alloc_text(&file, "# unstatable file: ");
        str_append_str(&file, &file2);
      } else {
        if (vsf_sysutil_statbuf_is_regfile(statbuf))
          str_replace_text(&file, "./", "+fmods/");
        else if (vsf_sysutil_statbuf_is_dir(statbuf))
          str_replace_text(&file, "./", "+dmods/");
        else
          str_replace_text(&file, "./", "+umods/");
      }
      str_append_char(&file, '\n');
      str_append_str(instr, &file);
    }
  } while(result);
  if (statbuf != 0)
  {
    vsf_sysutil_free(statbuf);
  }
  /* parse the manual update list */
  str_alloc_text(&filename, "/DATA/CHANGE/");
  str_append_str(&filename, modpack);
  result = str_fileread(&list1, str_getbuf(&filename), 100000);
  if (result < 0)
  {
    return -1;
  }
  pos = 0;
  str_alloc_text(&version_line, "@");
  str_append_str(&version_line, version);
  do
  {
    result = str_getline(&list1, &file, &pos);
    if (!result)
    {
      return -1;
    }
  } while(!str_equal(&file, &version_line));
  while(1)
  {
    int flags = 0;
    result = str_getline(&list1, &file, &pos);
    if (!result)
    {
      break;
    }
    if (str_getlen(&file) > 0) {
      str_mid_to_end(&file, &file2, 1);
      str_alloc_text(&filep, "+");
      str_alloc_text(&filem, "-");
      str_alloc_text(&filea, "*");
      str_append_str(&filep, &file2);
      str_append_str(&filem, &file2);
      str_append_str(&filea, &file2);
    }
    else
    {
      continue;
    }
    c = str_get_char_at(&file, 0);
    switch (c)
    {
      case '+':
        if (str_contains_line(instr, &filem))
        {
          str_replace_text(instr, str_getbuf(&filem), str_getbuf(&filea));
          break;
        }
        else if ((!str_contains_line(instr, &filep)) && (!str_contains_line(instr, &filea)))
        {
          str_append_char(&filep, '\n');
          str_append_str(instr, &filep);
        }
        break;
      case '-':
        if ((str_contains_line(instr, &filep)) || (str_contains_line(instr, &filea)))
        {
          str_append_char(&filep, '\n');
          str_append_char(&filea, '\n');
          str_replace_text(instr, str_getbuf(&filep), "");
          str_replace_text(instr, str_getbuf(&filea), "");
        }
        else if (!str_contains_line(instr, &filem))
        {
          str_append_char(&filem, '\n');
          str_append_str(instr, &filem);
        }
        break;
      case '*':
        if (str_contains_line(instr, &filem))
        {
          str_replace_text(instr, str_getbuf(&filem), str_getbuf(&filea));
          break;
        }
        else if (str_contains_line(instr, &filep))
        {
          str_replace_text(instr, str_getbuf(&filep), str_getbuf(&filea));
          break;
        }
        else if (!str_contains_line(instr, &filea))
        {
          str_append_char(&filea, '\n');
          str_append_str(instr, &filea);
        }
        break;
      default:
        continue;
    }
  }
  return 0;  
}
