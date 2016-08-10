#!/usr/bin/env bash

if ! [ `awk '/processor/ { count++ } END { print count }' /proc/cpuinfo` -eq 40 ]; then
  if [ -f /root/.cpufixed ]; then
    echo -e "You have another problem other than acpi.\n"
    echo -e "Investigate or you can continue at your own risk.\n"
    read -t 7 -p "Continue [Y/n]: " __UNDER40__
    if [ $__UNDER40__ == "n" ]; then
      exit 200
    fi
  fi
  if ! [ -f /acpi-fixed ]; then
    echo -e "No bueno! acpi=off or another issue.\n"
    echo -e "Fixing acpi=off. If this does not work, investigate further.\n"
    sed -i.bak 's/acpi=off/acpi=noirq/' /etc/default/grub
    grub-mkconfig -o /boot/grub/grub.cfg
    update-grub
    touch /root/.cpufixed
    shutdown -r now "REBOOTING YOUR SYSTEM. RE-RUN SCRIPT WHEN LOGGED BACK IN."
  fi
else
  echo -e "Good to go! acpi=noirq or bug irrelevant.\n"
  sleep 3
fi
